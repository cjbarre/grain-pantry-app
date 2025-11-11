(ns ai.obney.workshop.user-service.core.read-models
  "The core read-models namespace in a grain app is where projections are created from events.
   Events are retrieved using the event-store and the read model is built through reducing usually.
   These tend to be used by the other components of the grain app, such as commands, queries, periodic tasks, 
   and todo-processors."
  (:require [ai.obney.grain.event-store-v2.interface :as event-store]
            [com.brunobonacci.mulog :as u]))

(def user-event-types #{:user/signed-up 
                        :user/password-reset
                        :user/email-verified})

(defmulti apply-event
  (fn [_state event]
    (:event/type event)))

(defmethod apply-event :user/signed-up
  [state {:keys [user-id email-address password household-id]}]
  (-> state
      (assoc-in [user-id :user/id] user-id)
      (assoc-in [user-id :user/email-address] email-address)
      (assoc-in [user-id :user/password] password)
      (assoc-in [user-id :user/household-id] household-id)))


(defmethod apply-event :user/password-reset
  [state {:keys [user-id password]}]
  (assoc-in state [user-id :user/password] password))

(defmethod apply-event :user/email-verified
  [state {:keys [user-id]}]
  (assoc-in state [user-id :user/email-verified] true))

(defmethod apply-event :default
  [state _event]
  state)


(defn apply-events
  [events]
  (let [result (reduce
                (fn [state event]
                  (apply-event state event))
                {}
                events)]
    (when (seq result)
      result)))