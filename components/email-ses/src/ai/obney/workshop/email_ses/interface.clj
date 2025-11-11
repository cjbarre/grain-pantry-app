(ns ai.obney.workshop.email-ses.interface
  (:require [ai.obney.workshop.email-ses.core :as core]))

(defn ->SES
  [config]
  (core/->SES config))