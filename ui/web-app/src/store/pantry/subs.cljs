(ns store.pantry.subs
  "Re-frame subscriptions for pantry data"
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::pantry-items
  (fn [db _]
    (get-in db [:pantry :items] [])))

(rf/reg-sub
  ::shopping-list
  (fn [db _]
    (get-in db [:pantry :shopping-list] [])))

(rf/reg-sub
  ::recipes
  (fn [db _]
    (get-in db [:pantry :recipes] [])))

(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:pantry :loading] false)))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:pantry :error] nil)))
