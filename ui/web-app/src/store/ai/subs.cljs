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
