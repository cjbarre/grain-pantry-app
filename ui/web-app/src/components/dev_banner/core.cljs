(ns components.dev-banner.core
  (:require [uix.core :as uix :refer [defui $]]
            [components.context.interface :as context]))

(defui development-banner []
  (let [ctx (context/use-context)
        show-banner? (:dev/show-banner ctx)]
    (when show-banner?
      ($ :div {:class "bg-blue-100 text-blue-800 px-4 py-2 text-center text-sm font-medium border-b border-blue-200"}
         ($ :span "DEVELOPMENT ENVIRONMENT - Data will be cleared regularly")))))