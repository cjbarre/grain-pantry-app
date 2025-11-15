(ns store.recipes.effects
  "Re-frame effects for recipe operations"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::search-recipes
  (fn [{:keys [api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client
                           {:command/name :ai/search-recipes}))]
        (if (anomaly? response)
          (rf/dispatch (conj on-failure response))
          (rf/dispatch (conj on-success response)))))))
