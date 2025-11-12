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

(rf/reg-fx
  ::track-interaction
  (fn [{:keys [recipe interaction-type api-client]}]
    (go
      (let [command-name (case interaction-type
                          :view :ai/track-recipe-view
                          :dismiss :ai/track-recipe-dismiss
                          :cooked :ai/mark-recipe-cooked)
            recipe-id (:id recipe)
            recipe-title (:title recipe)]

        ;; Debug logging
        (js/console.log "Tracking interaction:" interaction-type)
        (js/console.log "Recipe object:" (clj->js recipe))
        (js/console.log "Recipe ID:" recipe-id)
        (js/console.log "Recipe title:" recipe-title)

        (when-not recipe-id
          (js/console.error "Missing recipe ID!" (clj->js recipe)))

        (let [response (<! (api/command api-client
                             {:command/name command-name
                              :recipe-id recipe-id
                              :recipe-title recipe-title}))]
          ;; Silent tracking - no UI feedback needed
          (when (anomaly? response)
            (js/console.warn "Failed to track recipe interaction:" response)))))))
