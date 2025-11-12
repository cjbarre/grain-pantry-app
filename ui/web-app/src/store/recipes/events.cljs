(ns store.recipes.events
  "Re-frame events for recipe operations"
  (:require [re-frame.core :as rf]
            [store.recipes.effects :as recipe-fx]
            [store.pantry.effects :as pantry-fx]))

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

;;
;; Shopping List Integration Events
;;

(rf/reg-event-fx
  ::add-missing-items-to-shopping
  (fn [_ [_ recipe api-client]]
    (js/console.log "Add missing items clicked!" recipe)
    (let [missing-ingredients (->> (:ingredients recipe)
                                   (filter #(false? (:have %)))
                                   (map (fn [ingredient]
                                          {:name (:name ingredient)
                                           :quantity (or (:quantity ingredient) "1")
                                           :category (or (:category ingredient) "Other")
                                           :for-recipe (:title recipe)})))]
      (js/console.log "Missing ingredients:" (clj->js missing-ingredients))
      (if (seq missing-ingredients)
        ;; Use :fx to dispatch multiple effects of the same type
        {:fx (concat
              ;; Add each ingredient
              (map (fn [ingredient]
                     [::pantry-fx/add-shopping-item
                      {:item ingredient
                       :api-client api-client
                       :on-success [::items-added-to-list]
                       :on-failure [::items-added-failure]}])
                   missing-ingredients)
              ;; Show success message
              [[:dispatch [::items-added-success (count missing-ingredients)]]])}
        {:dispatch [::no-missing-items]}))))

(rf/reg-event-db
  ::items-added-success
  (fn [db [_ count]]
    (js/console.log "Items added successfully!" count)
    (-> db
        (assoc-in [:ui :toast] {:message (str "Added " count " item" (when (> count 1) "s") " to shopping list")
                                :type :success})
        (assoc-in [:pantry :shopping-list-needs-refresh] true))))

(rf/reg-event-db
  ::items-added-to-list
  (fn [db _]
    ;; Individual item success - just track
    (js/console.log "Individual item added")
    db))

(rf/reg-event-db
  ::items-added-failure
  (fn [db [_ error]]
    (js/console.log "Failed to add items" error)
    (assoc-in db [:ui :toast] {:message "Failed to add items to shopping list"
                               :type :error})))

(rf/reg-event-db
  ::no-missing-items
  (fn [db _]
    (js/console.log "No missing items")
    (assoc-in db [:ui :toast] {:message "No missing items to add"
                               :type :info})))
