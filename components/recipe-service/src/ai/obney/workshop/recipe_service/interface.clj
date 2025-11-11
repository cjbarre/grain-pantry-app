(ns ai.obney.workshop.recipe-service.interface
  "Public interface for recipe-service component.

   Provides recipe matching and search capabilities based on
   pantry inventory."
  (:require [ai.obney.workshop.recipe-service.core.queries :as queries]))

;;
;; Query Registry
;;

(def queries queries/queries)

;; Recipe service is query-only (no commands or events)
;; It reads from pantry-service read models to match recipes
