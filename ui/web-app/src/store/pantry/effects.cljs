(ns store.pantry.effects
  "Re-frame effects for pantry API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

;;
;; Pantry Items Effects
;;

(rf/reg-fx
  ::fetch-pantry-items
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client {:query/name :pantry/get-items
                                                   :category nil
                                                   :search nil}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::add-pantry-item
  (fn [{:keys [item api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :pantry/add-item
                                                     :name (:name item)
                                                     :quantity (:quantity item)
                                                     :category (:category item)
                                                     :expires (:expires item)}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::remove-pantry-item
  (fn [{:keys [item-id api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :pantry/remove-item
                                                     :item-id item-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

;;
;; Shopping List Effects
;;

(rf/reg-fx
  ::fetch-shopping-list
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/query api-client {:query/name :shopping/get-list}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::add-shopping-item
  (fn [{:keys [item api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :shopping/add-item
                                                     :name (:name item)
                                                     :quantity (:quantity item)
                                                     :category (:category item)
                                                     :for-recipe (:for-recipe item)}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::toggle-shopping-item
  (fn [{:keys [item-id checked api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :shopping/toggle-item
                                                     :item-id item-id
                                                     :checked checked}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::remove-shopping-item
  (fn [{:keys [item-id api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :shopping/remove-item
                                                     :item-id item-id}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::clear-completed
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :shopping/clear-completed}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::move-to-pantry
  (fn [{:keys [item-ids api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :shopping/move-to-pantry
                                                     :item-ids item-ids}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))
