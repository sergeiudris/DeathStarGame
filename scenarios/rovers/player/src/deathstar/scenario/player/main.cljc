(ns deathstar.scenario.player.main
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [cljs.core.async.impl.protocols :refer [closed?]]
   [cljs.core.async.interop :refer-macros [<p!]]
   [goog.string.format :as format]
   [goog.string :refer [format]]
   [goog.object]
   [clojure.string :as string]
   [cljs.reader :refer [read-string]]

   [cljctools.csp.op.spec :as op.spec]
   [cljctools.cljc.core :as cljc.core]

   [cljctools.rsocket.spec :as rsocket.spec]
   [cljctools.rsocket.chan :as rsocket.chan]
   [cljctools.rsocket.impl :as rsocket.impl]

   [deathstar.scenario.spec :as scenario.spec]
   [deathstar.scenario.chan :as scenario.chan]
   [deathstar.scenario.core :as scenario.core]

   [deathstar.scenario.player.spec :as player.spec]
   [deathstar.scenario.player.chan :as player.chan]))

(goog-define RSOCKET_PORT 0)

(def channels (merge
               (scenario.chan/create-channels)
               (player.chan/create-channels)
               (rsocket.chan/create-channels)))

(pipe (::rsocket.chan/requests| channels) (::player.chan/ops| channels))

(pipe (::scenario.chan/ops| channels) (::rsocket.chan/ops| channels))

(def state (atom {}))

(comment

  (swap! state assoc :random (rand-int 10))

  (scenario.chan/op
   {::op.spec/op-key ::scenario.chan/move-rovers
    ::op.spec/op-type ::op.spec/fire-and-forget}
   channels
   {::scenario.spec/x (rand-int scenario.spec/x-size)
    ::scenario.spec/y (rand-int scenario.spec/y-size)})

  ;;
  )

(defn create-proc-ops
  [channels opts]
  (let [{:keys [::player.chan/ops|]} channels]
    (go
      (loop []
        (when-let [[value port] (alts! [ops|])]
          (condp = port
            ops|
            (condp = (select-keys value [::op.spec/op-key ::op.spec/op-type ::op.spec/op-orient])

              {::op.spec/op-key ::player.chan/init}
              (let [{:keys []} value]
                (println ::init)
                #_(go (loop []
                        (<! (timeout 3000))
                        (scenario.chan/op
                         {::op.spec/op-key ::scenario.chan/move-rovers
                          ::op.spec/op-type ::op.spec/fire-and-forget}
                         channels
                         {:random (rand-int 100)})
                        (recur))))

              {::op.spec/op-key ::player.chan/next-move
               ::op.spec/op-type ::op.spec/request-response
               ::op.spec/op-orient ::op.spec/request}
              (let [{:keys [::op.spec/out|
                            ::scenario.spec/step]} value
                    ops
                    [{::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/signal-tower}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/recharge}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/signal-tower}
                     {::op.spec/op-key ::scenario.chan/scan
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/energy-percentage 0.3}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/signal-tower}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/signal-tower}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/recharge}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/signal-tower}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/recharge}
                     {::op.spec/op-key ::scenario.chan/move-rovers
                      ::op.spec/op-type ::op.spec/fire-and-forget
                      ::scenario.spec/choose-location ::scenario.spec/closest
                      ::scenario.spec/location-type ::scenario.spec/signal-tower}]]
                (player.chan/op
                 {::op.spec/op-key ::player.chan/next-move
                  ::op.spec/op-type ::op.spec/request-response
                  ::op.spec/op-orient ::op.spec/response}
                 out|
                 {::scenario.chan/op  (get ops step)})))))
        (recur)))))

(def rsocket (rsocket.impl/create-proc-ops
              channels
              {::rsocket.spec/connection-side ::rsocket.spec/initiating
               ::rsocket.spec/host "localhost"
               ::rsocket.spec/port RSOCKET_PORT
               ::rsocket.spec/transport ::rsocket.spec/websocket}))

(def ops (create-proc-ops channels {}))

(defn ^:export main
  []
  (println ::main)
  (println ::RSOCKET_PORT RSOCKET_PORT)
  (player.chan/op
   {::op.spec/op-key ::player.chan/init}
   channels
   {}))


(do (main))