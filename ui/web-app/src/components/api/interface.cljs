(ns components.api.interface
  (:require [components.api.core :as core]))

;; Protocol functions that take client as first argument
(defn command [client cmd]
  (core/command client cmd))

(defn query [client qry]
  (core/query client qry))

;; Client creation
(def ->RemoteAPIClient core/->RemoteAPIClient)