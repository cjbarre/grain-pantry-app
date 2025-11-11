(ns ai.obney.workshop.url-presigner-aws.interface
  (:require [ai.obney.workshop.url-presigner-aws.core :as core]))

(defn ->URLPresignerAWS
  "Creates an S3 URL presigner instance."
  [config]
  (core/->URLPresignerAWS config))

