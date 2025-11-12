(ns store.recipes.subs
  "Re-frame subscriptions for recipe data"
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::ai-recipes
  (fn [db _]
    (get-in db [:recipes :ai-recipes] [])))

(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:recipes :loading] false)))

(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:recipes :error] nil)))
