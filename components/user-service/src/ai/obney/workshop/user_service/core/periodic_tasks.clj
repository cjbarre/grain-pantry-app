(ns ai.obney.workshop.user-service.core.periodic-tasks
  "The periodic tasks namespace in a grain service component is where
   periodic task functions are defined. These functions accept a context,
   which is wired up in the base for the grain app, and the time, provided 
   by the periodic-task component implementation.
   
   Periodic tasks are less rigid than commands and todo-processors and generally
   do not have a specific return value. So they use the various dependencies in the context
   in order to perform their work with discretion."
  (:require [com.brunobonacci.mulog :as u]))

(defn example-periodic-task
  [_context _time]
  (u/trace ::example-periodic-task []))