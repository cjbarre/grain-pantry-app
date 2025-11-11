(ns ai.obney.workshop.user-service.core.todo-processors
  "The core todo-processors namespace in a grain service is where todo-processor handler functions are defined.
   These functions receive a context and have a specific return signature. They can return a cognitect anomaly,
   a map with a `:result/events` key containing a sequence of valid events per the event-store event 
   schema, or an empty map. Sometimes the todo-processor will just call a command through the commant-processor.
   The wiring up of the context and the function occurs in the grain app base. The todo-processor subscribes to 
   one or more events via pubsub and only ever processes a single event at a time, which is included in the context."
  (:require [ai.obney.grain.command-processor.interface :as cp]
            [ai.obney.grain.time.interface :as time]))

(defn welcome-email-todo
  [{{:keys [user-id email-address]} :event :as context}]
  (cp/process-command
   (assoc context :command {:command/id (random-uuid)
                            :command/timestamp (time/now)
                            :command/name :user/send-welcome-email
                            :user-id user-id
                            :email-address email-address})))

(defn verification-email-todo
  [{{:keys [user-id email-address]} :event :as context}]
  (cp/process-command
   (assoc context :command {:command/id (random-uuid)
                            :command/timestamp (time/now)
                            :command/name :user/send-verification-link
                            :user-id user-id
                            :email-address email-address})))

(defn password-reset-notification-email-todo
  [{{:keys [user-id]} :event :as context}]
  (cp/process-command
   (assoc context :command {:command/id (random-uuid)
                            :command/timestamp (time/now)
                            :command/name :user/send-password-reset-notification
                            :user-id user-id})))

(def todo-processors
  {#_#_:user/welcome-email-todo
   {:handler-fn #'welcome-email-todo
    :topics [:user/signed-up]}

   :user/verification-email-todo
   {:handler-fn #'verification-email-todo
    :topics [:user/signed-up]}

   :user/password-reset-notification-email-todo
   {:handler-fn #'password-reset-notification-email-todo
    :topics [:user/password-reset]}})