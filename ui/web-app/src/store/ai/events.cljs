(ns store.ai.events
  "Re-frame events for AI interactions"
  (:require [re-frame.core :as rf]
            [store.ai.effects :as ai-fx]
            [store.pantry.events :as pantry-events]))

(rf/reg-event-fx
  ::ask-ai
  (fn [{:keys [db]} [_ question]]
    {:db (-> db
             (update-in [:ai :conversation] (fnil conj [])
               {:role "user" :content question})
             (assoc-in [:ai :loading] true))
     ::ai-fx/ask-ai {:question question
                     :api-client (:api/client db)
                     :on-success [::ask-ai-success]
                     :on-failure [::ask-ai-failure]}}))

(rf/reg-event-db
  ::ask-ai-success
  (fn [db [_ response]]
    (-> db
        (update-in [:ai :conversation] (fnil conj [])
          {:role "assistant"
           :content (:response response)
           :suggested-actions (:suggested-actions response)})
        (assoc-in [:ai :loading] false)
        (assoc-in [:ai :error] nil))))

(rf/reg-event-db
  ::ask-ai-failure
  (fn [db [_ error]]
    (-> db
        (update-in [:ai :conversation] (fnil conj [])
          {:role "assistant"
           :content "Sorry, I encountered an error. Please try again."})
        (assoc-in [:ai :loading] false)
        (assoc-in [:ai :error] error))))

(rf/reg-event-db
  ::clear-conversation
  (fn [db [_]]
    (assoc-in db [:ai :conversation] [])))

;;
;; AI Action Execution
;;

(defn- normalize-action
  "Normalize action params from DSPy (string keys) to Clojure (keyword keys)"
  [action]
  (let [params (get action "params" (:params action))
        normalized-params (if (map? params)
                           (into {} (map (fn [[k v]]
                                          [(if (string? k) (keyword k) k) v])
                                        params))
                           params)]
    {:type (keyword (get action "type" (:type action)))
     :description (get action "description" (:description action))
     :params normalized-params}))

(rf/reg-event-fx
  ::execute-suggested-action
  (fn [{:keys [db]} [_ action]]
    (let [api-client (:api/client db)
          normalized (normalize-action action)
          action-type (:type normalized)
          params (:params normalized)]

      ;; Map action type to appropriate command
      (case action-type
        :add-pantry-item
        {:dispatch [::pantry-events/add-pantry-item
                   params
                   api-client
                   #(rf/dispatch [::action-success normalized])
                   #(rf/dispatch [::action-failure normalized %])]}

        :add-shopping-item
        {:dispatch [::pantry-events/add-shopping-item
                   params
                   api-client
                   #(rf/dispatch [::action-success normalized])
                   #(rf/dispatch [::action-failure normalized %])]}

        ;; TODO: Add more action types (remove-pantry-item, move-to-pantry, etc.)

        ;; Default: show error for unknown action type
        {:db (update-in db [:ai :conversation] (fnil conj [])
               {:role "system"
                :content (str "❌ Unknown action type: " action-type)})}))))

(rf/reg-event-fx
  ::action-success
  (fn [{:keys [db]} [_ action]]
    (let [api-client (:api/client db)]
      {:db (update-in db [:ai :conversation] (fnil conj [])
             {:role "system"
              :content (str "✓ " (:description action) " completed successfully")})
       ;; Refresh pantry data after successful action
       :dispatch-n [[::pantry-events/fetch-pantry-items api-client]
                    [::pantry-events/fetch-shopping-list api-client]]})))

(rf/reg-event-db
  ::action-failure
  (fn [db [_ action _error]]
    (update-in db [:ai :conversation] (fnil conj [])
      {:role "system"
       :content (str "✗ Failed to " (:description action))})))
