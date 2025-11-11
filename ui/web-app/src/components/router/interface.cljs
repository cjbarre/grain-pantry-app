(ns components.router.interface
  (:require [components.router.core :as core]))

(def router core/router-outlet)
(def start-router! core/start-router!)
(def navigate! core/navigate!)