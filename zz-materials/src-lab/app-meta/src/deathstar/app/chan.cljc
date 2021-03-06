(ns deathstar.app.chan
  #?(:cljs (:require-macros [deathstar.app.chan]))
  (:require
   [clojure.core.async :as a :refer [chan go go-loop <! >!  take! put! offer! poll! alt! alts! close!
                                     pub sub unsub mult tap untap mix admix unmix pipe
                                     timeout to-chan  sliding-buffer dropping-buffer
                                     pipeline pipeline-async]]
   [clojure.spec.alpha :as s]
   [cljctools.csp.op.spec :as op.spec]
   [deathstar.app.spec :as app.spec]))

(do (clojure.spec.alpha/check-asserts true))

(defmulti ^{:private true} op* op.spec/op-spec-dispatch-fn)
(s/def ::op (s/multi-spec op* op.spec/op-spec-retag-fn))
(defmulti op op.spec/op-dispatch-fn)

(defn create-channels
  []
  (let [ops| (chan 10)]
    {::ops| ops|}))

(defmethod op*
  {::op.spec/op-key ::init
   ::op.spec/op-type ::op.spec/fire-and-forget} [_]
  (s/keys :req []))
(derive ::init ::op)
(defmethod op
  {::op.spec/op-key ::init
   ::op.spec/op-type ::op.spec/fire-and-forget}
  [op-meta channels value]
  (put! (::ops| channels) (merge op-meta
                                 value)))


(defmethod op*
  {::op.spec/op-key ::request-state-update
   ::op.spec/op-type ::op.spec/fire-and-forget} [_]
  (s/keys :req []))
(derive ::request-state-update ::op)
(defmethod op
  {::op.spec/op-key ::request-state-update
   ::op.spec/op-type ::op.spec/fire-and-forget}
  [op-meta channels value]
  (put! (::ops| channels) (merge op-meta
                                 value)))


(defmethod op*
  {::op.spec/op-key ::request-tournament-stream
   ::op.spec/op-type ::op.spec/request-stream
   ::op.spec/op-orient ::op.spec/request} [_]
  (s/keys :req []))
(derive ::request-tournament-stream ::op)
(defmethod op
  {::op.spec/op-key ::request-tournament-stream
   ::op.spec/op-type ::op.spec/request-stream
   ::op.spec/op-orient ::op.spec/request}
  ([op-meta channels value]
   (op op-meta channels value (chan 64)))
  ([op-meta channels value out|]
   (put! (::ops| channels) (merge op-meta
                                  value
                                  {::op.spec/out| out|}))
   out|))
(defmethod op*
  {::op.spec/op-key ::request-tournament-stream
   ::op.spec/op-type ::op.spec/request-stream
   ::op.spec/op-orient ::op.spec/response} [_]
  (s/keys :req []))
(derive ::request-tournament-stream ::op)
(defmethod op
  {::op.spec/op-key ::request-tournament-stream
   ::op.spec/op-type ::op.spec/request-stream
   ::op.spec/op-orient ::op.spec/response}
  [op-meta out| value]
  (put! out| (merge op-meta
                    value)))



