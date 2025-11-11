(ns repl-stuff
  (:require [ai.obney.workshop.web-api.core :as service]))


(comment

  ;;
  ;; Start Service
  ;;
  (do
    (def service (service/start))
    (def context (::service/context service))
    (def event-store (:event-store context)))

  ;;
  ;; Stop Service ;;
  ;;
  (service/stop service)

  "")