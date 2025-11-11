(ns store.core
  "Si-frame store initialization and core events"
  (:require [re-frame.core :as rf]))

(defn init-db
  "Initialize the application database with default state"
  []
  {:auth {:status :loading  ;; :loading | true | false
          :user nil}})       ;; {:email "..."})

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   (init-db)))
