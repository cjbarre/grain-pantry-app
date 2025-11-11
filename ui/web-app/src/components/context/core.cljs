(ns components.context.core
  (:require [uix.core :as uix]))

;; Global app context
(def app-context (uix/create-context nil))

;; Hook to access the app context
(defn use-context
  "Hook to get the entire app context map."
  []
  (let [ctx (uix/use-context app-context)]
    (when-not ctx
      (throw (js/Error. "App context not found. Ensure app is wrapped with app-provider.")))
    ctx))

;; Provider component
(uix/defui app-provider [{:keys [context children]}]
  (uix/$ (.-Provider app-context) {:value context}
    children))