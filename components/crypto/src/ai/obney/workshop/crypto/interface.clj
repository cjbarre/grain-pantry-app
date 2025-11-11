(ns ai.obney.workshop.crypto.interface
  (:require [ai.obney.workshop.crypto.interface.protocol :as p]))

(defn encrypt
  [provider args]
  (p/encrypt provider args))

(defn decrypt
  [provider args]
  (p/decrypt provider args))

