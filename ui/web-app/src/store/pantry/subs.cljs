(ns store.pantry.subs
  "Re-frame subscriptions for pantry data"
  (:require [re-frame.core :as rf]
            [clojure.string :as str]))

(rf/reg-sub
  ::pantry-items
  (fn [db _]
    (get-in db [:pantry :items] [])))

(rf/reg-sub
  ::shopping-list
  (fn [db _]
    (get-in db [:pantry :shopping-list] [])))

(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:pantry :loading] false)))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:pantry :error] nil)))

;;
;; Pantry Form Subscriptions
;;

(rf/reg-sub
  ::form-name
  (fn [db _]
    (get-in db [:pantry :form :name] "")))

(rf/reg-sub
  ::form-quantity
  (fn [db _]
    (get-in db [:pantry :form :quantity] "1")))

(rf/reg-sub
  ::form-category
  (fn [db _]
    (get-in db [:pantry :form :category] "")))

(rf/reg-sub
  ::form-expires
  (fn [db _]
    (get-in db [:pantry :form :expires] "")))

(rf/reg-sub
  ::form-expanded
  (fn [db _]
    (get-in db [:pantry :form :expanded] false)))

(rf/reg-sub
  ::form-error
  (fn [db _]
    (get-in db [:pantry :form :error] false)))

(rf/reg-sub
  ::form-valid?
  :<- [::form-name]
  :<- [::form-category]
  (fn [[name category] _]
    (and (not-empty (str/trim name))
         (not-empty category))))
