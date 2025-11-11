(ns ai.obney.workshop.email.interface
  (:refer-clojure :exclude [send])
  (:require [ai.obney.workshop.email.interface.protocol :as p]))

(defn send
  [email-client args]
  (p/send email-client args))

