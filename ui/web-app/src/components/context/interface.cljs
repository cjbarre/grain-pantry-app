(ns components.context.interface
  (:require [components.context.core :as core]))

(defn use-context []
  (core/use-context))

(def app-provider core/app-provider)