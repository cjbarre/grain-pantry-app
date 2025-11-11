(ns store.ai.events
  "Re-frame events for AI interactions"
  (:require [re-frame.core :as rf]
            [store.ai.effects :as ai-fx]))

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
          {:role "assistant" :content (:response response)})
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
