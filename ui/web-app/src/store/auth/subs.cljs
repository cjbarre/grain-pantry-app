(ns store.auth.subs
  "Auth-related re-frame subscriptions"
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::status
  (fn [db _]
    (get-in db [:auth :status])))

(rf/reg-sub
  ::user
  (fn [db _]
    (get-in db [:auth :user])))

(rf/reg-sub
  ::authenticated?
  :<- [::status]
  (fn [status _]
    (= status true)))

(rf/reg-sub
  ::loading?
  :<- [::status]
  (fn [status _]
    (= status :loading)))
