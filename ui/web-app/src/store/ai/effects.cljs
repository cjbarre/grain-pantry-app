(ns store.ai.effects
  "Re-frame effects for AI API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::ask-ai
  (fn [{:keys [question api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client
                             {:command/name :ai/ask
                              :question question}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))
