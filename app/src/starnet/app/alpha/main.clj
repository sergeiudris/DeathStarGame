(ns starnet.app.alpha.main
  (:require
   [clojure.core.async :as a :refer [<! >! <!! timeout chan alt! go
                                     >!! <!! alt!! alts! alts!! take! put!
                                     thread pub sub]]
   [starnet.app.alpha.aux.nrepl :refer [start-nrepl-server]]
   [clojure.spec.alpha :as s]
   [clojure.spec.test.alpha :as stest]

   [starnet.app.alpha.aux.serdes]

   [starnet.common.alpha.spec]
   [starnet.app.alpha.spec]
   [starnet.common.pad.all]

   [starnet.app.alpha.repl]
   [starnet.app.alpha.tests]
   [starnet.app.alpha.crux]
   [starnet.app.alpha.http]
   [starnet.app.crux-samples.core]

   [starnet.app.alpha.streams :refer [create-topics list-topics
                                      delete-topics produce-event
                                      future-call-consumer read-store
                                      send-event create-streams-game create-streams-user]]
   [starnet.app.alpha.http  :as app-http]
   [starnet.app.alpha.crux :as app-crux]
   [crux.api :as crux])
  (:import
   org.apache.kafka.common.KafkaFuture$BiConsumer))

(declare env-optimized? proc-main proc-http-server
         proc-derived-1 proc-topics proc-streams proc-log
         proc-cruxdb)

(def cmain (chan 1))
(def csys (chan (a/sliding-buffer 10)))
(def psys (pub csys first))
(def derived-1 (atom {}))
(def cdb (chan 10))

(defn -main  [& args]
  (proc-derived-1  psys derived-1)
  (proc-topics psys csys)
  (proc-streams psys csys)
  (proc-http-server psys)
  (proc-cruxdb psys cdb)
  #_(put! csys [:cruxdb :start])
  #_(put! csys [:http-server :start])
  (put! cmain :start)
  (<!! (proc-main cmain)))

(comment

  (put! csys [:http-server :start])

  (put! csys [:cruxdb :start])
  (put! csys [:cruxdb :close])
  
  (stest/unstrument)

  (put! cmain :exit)
  ;;
  )

(defn env-optimized?
  []
  (let [appenv (read-string (System/getenv "appenv"))]
    (:optimized appenv)))

(defn proc-main
  [cmain]
  (go (loop [nrepl-server nil]
        (when-let [v (<! cmain)]
          (condp = v
            :start (let [sr (start-nrepl-server "0.0.0.0" 7788)]
                     (when-not (env-optimized?)
                       (stest/instrument)
                       (s/check-asserts true))
                     (when (env-optimized?)
                       (alter-var-root #'clojure.test/*load-tests* (fn [_] false)))
                     (recur sr))
            :stop (recur nrepl-server)
            :exit (System/exit 0))))
      (println "closing proc-main")))

(defn proc-http-server
  [psys]
  (let [c (chan 1)]
    (sub psys :http-server c)
    (go (loop [server nil]
          (when-let [[_ v] (<! c)]
            (condp = v
              :start (let [sr (app-http/-main-dev)]
                       (recur sr))
              :stop (recur server))))
        (println "closing proc-http-server"))))

(defn proc-log
  [psys]
  (let [c (chan 1)]
    (sub psys :log c)
    (go (loop []
          (if-let [[_ s] (<! c)]
            (println (str "; " s))
            (recur)))
        (println "closing proc-http-server"))))



(def crux-conf {:crux.node/topology '[crux.kafka/topology
                                      crux.kv.rocksdb/kv-store]
                :crux.kafka/bootstrap-servers "broker1:9092"
                :crux.kafka/tx-topic "crux-transaction-log"
                :crux.kafka/doc-topic "crux-docs"
                :crux.kafka/create-topics true
                :crux.kafka/doc-partitions 1
                :crux.kafka/replication-factor (short 1)
                :crux.kv/db-dir "/ctx/data/crux"
                :crux.kv/sync? false
                :crux.kv/check-and-store-index-version true})

(defn proc-dbcall
  [f args cout]
  (go
    (let [x (f args)]
      (>! cout x))))

(defn proc-cruxdb
  [psys cdb]
  (let [c (chan 1)]
    (sub psys :cruxdb c)
    (go (loop [node nil]
          (if-let [[vl port] (alts! (if node [c cdb] [c]))] ; add check if node is valid
            (condp = port
              c (condp = (second vl)
                  :start (let [n (crux/start-node crux-conf)]
                           (alter-var-root #'app-crux/node (constantly n)) ; for dev purposes
                           (println "; crux node started")
                           (recur n))
                  :close (do
                           (.close node)
                           (alter-var-root #'app-crux/node (constantly nil)) ; for dev purposes
                           (println "; crux node closed")
                           (recur nil)))
              cdb (do
                    (apply proc-dbcall vl)
                    (recur node)))))
        (println "closing proc-cruxdb"))))


(def kprops {"bootstrap.servers" "broker1:9092"})

(def ktopics ["alpha.user"
              "alpha.user.changes"
              "alpha.game"
              "alpha.game.changes"])

(defn proc-topics
  [psys csys]
  (let [c (chan 1)]
    (sub psys :ktopics c)
    (go (loop []
          (when-let [[_ v] (<! c)]
            (condp = v
              :create (do
                        (-> (create-topics {:props kprops
                                            :names ktopics
                                            :num-partitions 1
                                            :replication-factor 1})
                            (.all)
                            (.whenComplete
                             (reify KafkaFuture$BiConsumer
                               (accept [this res err]
                                 (println "topics created")
                                 (>! csys [:ktopics-created res]))))))
              :delete (delete-topics {:props kprops :names ktopics})))
          (recur))
        (println "proc-topics exiting"))
    c))


(comment

  (-> (create-topics {:props kprops
                      :names ktopics
                      :num-partitions 1
                      :replication-factor 1})
      (.all)
      (.whenComplete
       (reify KafkaFuture$BiConsumer
         (accept [this res err]
           (println "; created topics " ktopics)))))

  (delete-topics {:props kprops :names ktopics})
  (list-topics {:props kprops})


  ;;
  )

(defn proc-streams
  [psys csys]
  (let [c (chan 1)]
    (sub psys :kstreams c)
    (go (loop [app-state nil]
          (when-let [[t [k args]] (<! c)]
            (condp = k
              :create (let [{:keys [create id]} args
                            app (create)]
                        (>! csys [:kv [id app]])
                        (recur app))
              :close (do
                       (.close (:kstreams app-state))
                       (recur app-state))
              :start (do (.start (:kstreams app-state))
                         (recur app-state))
              :cleanup (do (.cleanUp (:kstreams app-state))
                           (recur app-state)))))
        (println "proc-streams exiting"))
    c))

(defn proc-derived-1
  [psys derived]
  (let [c (chan 1)]
    (sub psys :kv c)
    (go (loop []
          (when-let [[t [k v]] (<! c)]
            (do
              (swap! derived assoc k v)))
          (recur))
        (println "proc-view exiting"))
    c))

(comment

  (put! sys-chan-1 [:ktopics :create])
  (list-topics {:props props})
  (put! sys-chan-1 [:ktopics :delete])
  (list-topics {:props props})

  (put! sys-chan-1 [:kstreams [:create {:create create-streams-user
                                        :id :create-streams-user}]])

  @view-1
  (def streams-user (-> @view-1 :create-streams-user :kstreams))
  (.isRunning (.state streams-user))
  (put! sys-chan-1 [:kstreams [:start {}]])
  (put! sys-chan-1 [:kstreams [:close {}]])

  ;;
  )

