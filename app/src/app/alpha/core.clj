(ns app.alpha.core
  (:require [clojure.pprint :as pp]
            [app.alpha.spec :as spec]
            
            [app.alpha.streams.core :refer [create-topics list-topics
                                            delete-topics]]
            [app.alpha.part :as part]
            [app.alpha.streams.users :as streams-users]
            [app.alpha.streams.games :as streams-games]
            [app.alpha.streams.broadcast :as streams-broadcast])
  (:import
   org.apache.kafka.common.KafkaFuture$BiConsumer))

(comment

  create-user
  delete-account
  change-username
  change-email
  list-users
  list-user-account
  list-user-ongoing-games
  list-user-game-history
  create-event
  :event.type/single-elemination-bracket
  :event/start-ts
  cancel-event
  signin-event
  signout-event
  list-events
  list-event-signedup-users
  create-game
  cancel-game
  start-game
  end-game
  list-games
  join-game
  invite-into-game
  connect-to-game
  disconnect-from-game
  ingame-event
  list-ingame-events-for-game
  
  ;;
  )

(def props {"bootstrap.servers" "broker1:9092"})

(def topics ["alpha.user.data"
             "alpha.user.data.changes"
             "alpha.game.events"
             "alpha.game.events.changes"])

(defn mount
  []
  (-> (create-topics {:props props
                      :names topics
                      :num-partitions 1
                      :replication-factor 1})
      (.all)
      (.whenComplete
       (reify KafkaFuture$BiConsumer
         (accept [this res err]
           (println "; created topics")
           #_(streams-users/mount)
           #_(streams-games/mount)
           #_(streams-broadcast/mount))))))

(defn unmount
  []
  (streams-users/unmount)
  (streams-games/unmount)
  (streams-broadcast/unmount))

(comment

  (mount)

  (unmount)

  (list-topics {:props props})

  (delete-topics {:props props :names topics})

  (def producer (KafkaProducer.
                 {"bootstrap.servers" "broker1:9092"
                  "auto.commit.enable" "true"
                  "key.serializer" "app.kafka.serdes.TransitJsonSerializer"
                  "value.serializer" "app.kafka.serdes.TransitJsonSerializer"}))

  ;;
  )