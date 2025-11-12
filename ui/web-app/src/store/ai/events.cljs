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

(rf/reg-event-fx
  ::ask-ai-success
  (fn [{:keys [db]} [_ response]]
    (let [auto-accept? (get-in db [:ai :auto-accept] false)
          suggested-actions (:suggested-actions response)
          has-actions? (seq suggested-actions)
          ;; Message index will be current conversation count (before adding this message)
          message-idx (count (get-in db [:ai :conversation] []))]

      {:db (-> db
               (update-in [:ai :conversation] (fnil conj [])
                 {:role "assistant"
                  :content (:response response)
                  :suggested-actions suggested-actions})
               (assoc-in [:ai :loading] false)
               (assoc-in [:ai :error] nil))

       ;; Auto-execute actions if auto-accept is enabled
       :dispatch-n (if (and auto-accept? has-actions?)
                     [[::execute-actions-batch suggested-actions message-idx]]
                     [])})))

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

        :remove-pantry-item
        {:dispatch-n [[::pantry-events/remove-pantry-item
                      (let [id (get params :item-id)]
                        (if (string? id) (uuid id) id))
                      api-client]
                     [::action-success normalized]]}

        :remove-shopping-item
        {:dispatch-n [[::pantry-events/remove-shopping-item
                      (let [id (get params :item-id)]
                        (if (string? id) (uuid id) id))
                      api-client]
                     [::action-success normalized]]}

        :move-to-pantry
        {:dispatch-n [[::pantry-events/move-to-pantry
                      (let [ids (get params :item-ids)]
                        (if (every? string? ids)
                          (mapv uuid ids)
                          ids))
                      api-client]
                     [::action-success normalized]]}

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

;;
;; Batch Action Execution
;;

(rf/reg-event-fx
  ::execute-actions-batch
  (fn [{:keys [db]} [_ actions message-idx]]
    (if (empty? actions)
      ;; No actions to execute
      {:db db}
      ;; Initialize batch execution state and start first action
      (let [normalized-actions (mapv normalize-action actions)]
        {:db (assoc-in db [:ai :executing-batch]
               {:message-idx message-idx
                :current-index 0
                :total (count normalized-actions)
                :actions normalized-actions
                :status :executing})
         :dispatch [::execute-batch-action 0]}))))

(rf/reg-event-fx
  ::execute-batch-action
  (fn [{:keys [db]} [_ action-index]]
    (let [batch-state (get-in db [:ai :executing-batch])
          action (get-in batch-state [:actions action-index])
          action-type (:type action)
          params (:params action)
          api-client (:api/client db)]

      ;; Execute the action based on its type
      (case action-type
        :add-pantry-item
        {:dispatch [::pantry-events/add-pantry-item
                   params
                   api-client
                   #(rf/dispatch [::batch-action-completed action-index])
                   #(rf/dispatch [::batch-action-failed action-index action %])]}

        :add-shopping-item
        {:dispatch [::pantry-events/add-shopping-item
                   params
                   api-client
                   #(rf/dispatch [::batch-action-completed action-index])
                   #(rf/dispatch [::batch-action-failed action-index action %])]}

        :remove-pantry-item
        ;; Note: remove-pantry-item event doesn't accept callbacks, so we dispatch completion immediately
        ;; Parse string UUID to UUID object if needed
        {:dispatch-n [[::pantry-events/remove-pantry-item
                      (let [id (get params :item-id)]
                        (if (string? id) (uuid id) id))
                      api-client]
                     [::batch-action-completed action-index]]}

        :remove-shopping-item
        {:dispatch-n [[::pantry-events/remove-shopping-item
                      (let [id (get params :item-id)]
                        (if (string? id) (uuid id) id))
                      api-client]
                     [::batch-action-completed action-index]]}

        :move-to-pantry
        {:dispatch-n [[::pantry-events/move-to-pantry
                      (let [ids (get params :item-ids)]
                        (if (every? string? ids)
                          (mapv uuid ids)
                          ids))
                      api-client]
                     [::batch-action-completed action-index]]}

        ;; Default: unknown action type - treat as failure
        {:dispatch [::batch-action-failed action-index action
                   {:error "Unknown action type"}]}))))

(rf/reg-event-fx
  ::batch-action-completed
  (fn [{:keys [db]} [_ action-index]]
    (let [batch-state (get-in db [:ai :executing-batch])
          total (:total batch-state)
          next-index (inc action-index)]

      (if (< next-index total)
        ;; More actions to execute
        {:db (assoc-in db [:ai :executing-batch :current-index] next-index)
         :dispatch [::execute-batch-action next-index]}

        ;; All actions completed successfully
        {:dispatch [::batch-all-completed]}))))

(rf/reg-event-fx
  ::batch-action-failed
  (fn [{:keys [db]} [_ action-index action _error]]
    (let [batch-state (get-in db [:ai :executing-batch])
          total (:total batch-state)
          message-idx (:message-idx batch-state)]
      {:db (-> db
               ;; Clear batch execution state
               (assoc-in [:ai :executing-batch] nil)
               ;; Mark this message's actions as executed (even though failed)
               (update-in [:ai :executed-batches] (fnil conj #{}) message-idx)
               ;; Add failure message to conversation
               (update-in [:ai :conversation] (fnil conj [])
                 {:role "system"
                  :content (str "✗ Failed at action " (inc action-index) "/" total ": "
                               (:description action))}))
       ;; Still refresh data in case some actions succeeded
       :dispatch-n [[::pantry-events/fetch-pantry-items (:api/client db)]
                    [::pantry-events/fetch-shopping-list (:api/client db)]]})))

(rf/reg-event-fx
  ::batch-all-completed
  (fn [{:keys [db]} [_]]
    (let [batch-state (get-in db [:ai :executing-batch])
          total (:total batch-state)
          message-idx (:message-idx batch-state)]
      {:db (-> db
               ;; Clear batch execution state
               (assoc-in [:ai :executing-batch] nil)
               ;; Mark this message's actions as executed
               (update-in [:ai :executed-batches] (fnil conj #{}) message-idx)
               ;; Add success message to conversation
               (update-in [:ai :conversation] (fnil conj [])
                 {:role "system"
                  :content (str "✓ All " total " action" (when (> total 1) "s") " completed successfully")}))
       ;; Refresh data once after all actions complete
       :dispatch-n [[::pantry-events/fetch-pantry-items (:api/client db)]
                    [::pantry-events/fetch-shopping-list (:api/client db)]]})))

;;
;; Auto-Accept Toggle
;;

(rf/reg-event-db
  ::toggle-auto-accept
  (fn [db [_ enabled?]]
    (assoc-in db [:ai :auto-accept] enabled?)))
