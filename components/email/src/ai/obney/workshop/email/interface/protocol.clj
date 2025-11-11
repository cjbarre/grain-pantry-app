(ns ai.obney.workshop.email.interface.protocol
  (:refer-clojure :exclude [send]))

(defprotocol Email
  (send [this args]))