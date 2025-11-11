(ns ai.obney.workshop.crypto-kms.interface
  (:require [ai.obney.workshop.crypto-kms.core :as core]))

(defn ->KmsCryptoProvider
  [config]
  (core/->KmsCryptoProvider config))

