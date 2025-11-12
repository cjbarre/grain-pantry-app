(ns store.recipes.events
  "Re-frame events for recipe operations"
  (:require [re-frame.core :as rf]
            [store.recipes.effects :as recipe-fx]))

;;
;; Recipe Search Events
;;

(rf/reg-event-fx
  ::fetch-ai-recipes
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:recipes :loading] true)
     ::recipe-fx/search-recipes {:api-client api-client
                                  :on-success [::set-ai-recipes]
                                  :on-failure [::search-failed]}}))

(rf/reg-event-db
  ::set-ai-recipes
  (fn [db [_ response]]
    (-> db
        (assoc-in [:recipes :ai-recipes] (:recipes response))
        (assoc-in [:recipes :loading] false)
        (assoc-in [:recipes :error] nil))))

(rf/reg-event-db
  ::search-failed
  (fn [db [_ error]]
    (-> db
        (assoc-in [:recipes :loading] false)
        (assoc-in [:recipes :error] (or (:message error) "Failed to search recipes")))))

;;
;; Recipe Interaction Tracking Events
;;

(rf/reg-event-fx
  ::track-view
  (fn [{:keys [db]} [_ recipe api-client]]
    {::recipe-fx/track-interaction {:recipe recipe
                                     :interaction-type :view
                                     :api-client api-client}}))

(rf/reg-event-fx
  ::track-dismiss
  (fn [{:keys [db]} [_ recipe api-client]]
    {::recipe-fx/track-interaction {:recipe recipe
                                     :interaction-type :dismiss
                                     :api-client api-client}}))

(rf/reg-event-fx
  ::mark-cooked
  (fn [{:keys [db]} [_ recipe api-client]]
    {::recipe-fx/track-interaction {:recipe recipe
                                     :interaction-type :cooked
                                     :api-client api-client}}))
