(ns ai.obney.workshop.pantry-service.core.todo-processors
  "Todo processors for pantry-service background tasks.

   These processors subscribe to events and can trigger follow-up
   commands or external actions (like sending notifications)."
  (:require [ai.obney.grain.command-processor.interface :as cp]
            [ai.obney.grain.time.interface :as time]
            [ai.obney.grain.event-store-v2.interface :refer [->event]]
            [com.brunobonacci.mulog :as u]))

(defn create-household-for-new-user
  "Auto-create household when user signs up"
  [{{:keys [user-id household-id email-address]} :event
    :keys [event-store] :as context}]
  (u/log ::creating-household-for-user
         :user-id user-id
         :household-id household-id)
  {:result/events
   [(->event {:type :household/created
              :tags #{[:household household-id]}
              :body {:household-id household-id
                     :household-name (str email-address "'s Household")
                     :created-by user-id}})]})

(defn log-pantry-item-added
  "Example processor that logs when items are added to pantry"
  [{{:keys [household-id name]} :event :as context}]
  (u/log ::pantry-item-added
         :household-id household-id
         :item-name name)
  {})

(defn log-shopping-item-completed
  "Example processor that logs when shopping items are checked"
  [{{:keys [household-id checked]} :event :as context}]
  (when checked
    (u/log ::shopping-item-completed
           :household-id household-id))
  {})

;; TODO: Add processor to send notifications for expiring items
;; This would subscribe to periodic task events and trigger notifications
;; when items are about to expire

(def todo-processors
  {:pantry/create-household-for-user
   {:handler-fn #'create-household-for-new-user
    :topics [:user/signed-up]}

   :pantry/log-item-added
   {:handler-fn #'log-pantry-item-added
    :topics [:pantry-item/added]}

   :pantry/log-shopping-completed
   {:handler-fn #'log-shopping-item-completed
    :topics [:shopping-item/checked]}})
