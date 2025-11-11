(ns components.auth.interface
  (:require [components.auth.core :as core]))

(def main core/main)
(def logout! core/logout!)
(def check-auth-status! core/check-auth-status!)