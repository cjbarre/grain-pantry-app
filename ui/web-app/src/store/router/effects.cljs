(ns store.router.effects
  "Re-frame effects for router navigation"
  (:require [re-frame.core :as rf]))

(rf/reg-fx
  ::navigate
  (fn [{:keys [route-name params navigate-fn]}]
    (when navigate-fn
      (if params
        (navigate-fn route-name params)
        (navigate-fn route-name)))))
