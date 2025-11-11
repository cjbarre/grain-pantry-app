(ns ai.obney.workshop.user-service.interface
  (:require [ai.obney.workshop.user-service.core.commands :as commands]
            [ai.obney.workshop.user-service.core.periodic-tasks :as tasks]
            [ai.obney.workshop.user-service.core.queries :as queries]
            [ai.obney.workshop.user-service.core.read-models :as rm]
            [ai.obney.workshop.user-service.core.todo-processors :as tp]))

(def commands commands/commands)

(def periodic-tasks
  {:example-periodic-task {:handler-fn #'tasks/example-periodic-task
                           :schedule "0 0 * * * ?"  ;; Every hour
                           :description "Example periodic task"}})

(def queries queries/queries)

(def todo-processors tp/todo-processors)

;;
;; Read Models
;;

(defn apply-events
  [events]
  (rm/apply-events events))

(def user-event-types rm/user-event-types)