(ns store.ai.subs
  "Re-frame subscriptions for AI state"
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::conversation
  (fn [db _]
    (get-in db [:ai :conversation] [])))

(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:ai :loading] false)))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:ai :error] nil)))

(rf/reg-sub
  ::executing-batch
  (fn [db _]
    (get-in db [:ai :executing-batch] nil)))

(rf/reg-sub
  ::executed-batches
  (fn [db _]
    (get-in db [:ai :executed-batches] #{})))

(rf/reg-sub
  ::auto-accept
  (fn [db _]
    (get-in db [:ai :auto-accept] false)))
