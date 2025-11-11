(ns ai.obney.workshop.pantry-service.interface
  "Public interface for pantry-service component.

   Exports command and query registries, processors, and tasks
   to be wired up in the application base."
  (:require [ai.obney.workshop.pantry-service.core.commands :as commands]
            [ai.obney.workshop.pantry-service.core.queries :as queries]
            [ai.obney.workshop.pantry-service.core.todo-processors :as tp]
            [ai.obney.workshop.pantry-service.core.periodic-tasks :as tasks]
            [ai.obney.workshop.pantry-service.core.read-models :as rm]))

;;
;; Command and Query Registries
;;

(def commands commands/commands)

(def queries queries/queries)

;;
;; Background Processors
;;

(def todo-processors tp/todo-processors)

(def periodic-tasks
  {:pantry/check-expiring-items
   {:handler-fn #'tasks/check-expiring-items
    :schedule "0 0 9 * * ?"  ;; Every day at 9 AM
    :description "Check for items expiring soon and send notifications"}})

;;
;; Read Models
;;

(defn apply-household-events
  [events]
  (rm/apply-household-events events))

(defn apply-pantry-events
  [events]
  (rm/apply-pantry-events events))

(defn apply-shopping-events
  [events]
  (rm/apply-shopping-events events))

(def household-event-types rm/household-event-types)
(def pantry-event-types rm/pantry-event-types)
(def shopping-event-types rm/shopping-event-types)
(def all-event-types rm/all-event-types)
