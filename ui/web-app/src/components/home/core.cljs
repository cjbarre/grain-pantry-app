(ns components.home.core
  (:require [uix.core :as uix :refer [defui $]]))

(defui main []
  ($ :div {:class "flex items-center justify-center min-h-screen"}
     ($ :h1 {:class "text-4xl font-bold"} "Welcome Home!")))
