(ns store.pantry.events
  "Re-frame events for pantry management"
  (:require [re-frame.core :as rf]
            [store.pantry.effects :as pantry-fx]))

;;
;; Pantry Items State Management
;;

(rf/reg-event-db
  ::set-pantry-items
  (fn [db [_ items]]
    (assoc-in db [:pantry :items] items)))

(rf/reg-event-db
  ::set-shopping-list
  (fn [db [_ items]]
    (assoc-in db [:pantry :shopping-list] items)))

(rf/reg-event-db
  ::set-recipes
  (fn [db [_ recipes]]
    (assoc-in db [:pantry :recipes] recipes)))

(rf/reg-event-db
  ::set-loading
  (fn [db [_ loading?]]
    (assoc-in db [:pantry :loading] loading?)))

(rf/reg-event-db
  ::set-error
  (fn [db [_ error]]
    (assoc-in db [:pantry :error] error)))

;;
;; Fetch Pantry Items
;;

(rf/reg-event-fx
  ::fetch-pantry-items
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:pantry :loading] true)
     ::pantry-fx/fetch-pantry-items {:api-client api-client
                                      :on-success [::fetch-pantry-items-success]
                                      :on-failure [::fetch-pantry-items-failure]}}))

(rf/reg-event-db
  ::fetch-pantry-items-success
  (fn [db [_ items]]
    (-> db
        (assoc-in [:pantry :items] items)
        (assoc-in [:pantry :loading] false)
        (assoc-in [:pantry :error] nil))))

(rf/reg-event-db
  ::fetch-pantry-items-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:pantry :loading] false)
        (assoc-in [:pantry :error] error))))

;;
;; Add Pantry Item
;;

(rf/reg-event-fx
  ::add-pantry-item
  (fn [_ctx [_ item api-client on-success on-failure]]
    {::pantry-fx/add-pantry-item {:item item
                                   :api-client api-client
                                   :on-success [::add-pantry-item-success on-success]
                                   :on-failure [::add-pantry-item-failure on-failure]}}))

(rf/reg-event-db
  ::add-pantry-item-success
  (fn [db [_ on-success result]]
    (when on-success
      (on-success result))
    db))

(rf/reg-event-db
  ::add-pantry-item-failure
  (fn [db [_ on-failure error]]
    (when on-failure
      (on-failure error))
    db))

;;
;; Fetch Shopping List
;;

(rf/reg-event-fx
  ::fetch-shopping-list
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:pantry :loading] true)
     ::pantry-fx/fetch-shopping-list {:api-client api-client
                                       :on-success [::fetch-shopping-list-success]
                                       :on-failure [::fetch-shopping-list-failure]}}))

(rf/reg-event-db
  ::fetch-shopping-list-success
  (fn [db [_ items]]
    (-> db
        (assoc-in [:pantry :shopping-list] items)
        (assoc-in [:pantry :loading] false)
        (assoc-in [:pantry :error] nil))))

(rf/reg-event-db
  ::fetch-shopping-list-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:pantry :loading] false)
        (assoc-in [:pantry :error] error))))

;;
;; Add Shopping Item
;;

(rf/reg-event-fx
  ::add-shopping-item
  (fn [_ctx [_ item api-client on-success on-failure]]
    {::pantry-fx/add-shopping-item {:item item
                                     :api-client api-client
                                     :on-success [::add-shopping-item-success on-success]
                                     :on-failure [::add-shopping-item-failure on-failure]}}))

(rf/reg-event-db
  ::add-shopping-item-success
  (fn [db [_ on-success result]]
    (when on-success
      (on-success result))
    db))

(rf/reg-event-db
  ::add-shopping-item-failure
  (fn [db [_ on-failure error]]
    (when on-failure
      (on-failure error))
    db))

;;
;; Toggle Shopping Item
;;

(rf/reg-event-fx
  ::toggle-shopping-item
  (fn [_ctx [_ item-id checked api-client]]
    {::pantry-fx/toggle-shopping-item {:item-id item-id
                                        :checked checked
                                        :api-client api-client
                                        :on-success [::toggle-shopping-item-success api-client]
                                        :on-failure [::toggle-shopping-item-failure]}}))

(rf/reg-event-fx
  ::toggle-shopping-item-success
  (fn [{:keys [db]} [_ api-client]]
    {:db db
     :dispatch [::fetch-shopping-list api-client]}))

(rf/reg-event-db
  ::toggle-shopping-item-failure
  (fn [db [_ error]]
    (assoc-in db [:pantry :error] error)))

;;
;; Remove Shopping Item
;;

(rf/reg-event-fx
  ::remove-shopping-item
  (fn [_ctx [_ item-id api-client]]
    {::pantry-fx/remove-shopping-item {:item-id item-id
                                        :api-client api-client
                                        :on-success [::remove-shopping-item-success api-client]
                                        :on-failure [::remove-shopping-item-failure]}}))

(rf/reg-event-fx
  ::remove-shopping-item-success
  (fn [{:keys [db]} [_ api-client]]
    {:db db
     :dispatch [::fetch-shopping-list api-client]}))

(rf/reg-event-db
  ::remove-shopping-item-failure
  (fn [db [_ error]]
    (assoc-in db [:pantry :error] error)))

;;
;; Clear Completed Items
;;

(rf/reg-event-fx
  ::clear-completed
  (fn [_ctx [_ api-client]]
    {::pantry-fx/clear-completed {:api-client api-client
                                   :on-success [::clear-completed-success api-client]
                                   :on-failure [::clear-completed-failure]}}))

(rf/reg-event-fx
  ::clear-completed-success
  (fn [{:keys [db]} [_ api-client _result]]
    {:db db
     :dispatch [::fetch-shopping-list api-client]}))

(rf/reg-event-db
  ::clear-completed-failure
  (fn [db [_ error]]
    (assoc-in db [:pantry :error] error)))

;;
;; Move Items to Pantry
;;

(rf/reg-event-fx
  ::move-to-pantry
  (fn [_ctx [_ item-ids api-client]]
    {::pantry-fx/move-to-pantry {:item-ids item-ids
                                  :api-client api-client
                                  :on-success [::move-to-pantry-success api-client]
                                  :on-failure [::move-to-pantry-failure]}}))

(rf/reg-event-fx
  ::move-to-pantry-success
  (fn [{:keys [db]} [_ api-client _result]]
    {:db db
     :dispatch-n [[::fetch-shopping-list api-client]
                  [::fetch-pantry-items api-client]]}))

(rf/reg-event-db
  ::move-to-pantry-failure
  (fn [db [_ error]]
    (assoc-in db [:pantry :error] error)))

;;
;; Fetch Recipe Matches
;;

(rf/reg-event-fx
  ::fetch-recipe-matches
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:pantry :loading] true)
     ::pantry-fx/fetch-recipe-matches {:api-client api-client
                                        :on-success [::fetch-recipe-matches-success]
                                        :on-failure [::fetch-recipe-matches-failure]}}))

(rf/reg-event-db
  ::fetch-recipe-matches-success
  (fn [db [_ recipes]]
    (-> db
        (assoc-in [:pantry :recipes] recipes)
        (assoc-in [:pantry :loading] false)
        (assoc-in [:pantry :error] nil))))

(rf/reg-event-db
  ::fetch-recipe-matches-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:pantry :loading] false)
        (assoc-in [:pantry :error] error))))
