(ns store.core
  "Si-frame store initialization and core events"
  (:require [re-frame.core :as rf]))

(defn init-db
  "Initialize the application database with default state"
  []
  {:auth {:status :loading  ;; :loading | true | false
          :user nil}        ;; {:email "..."}
   :pantry {:items []
            :shopping-list []
            :recipes []
            :loading false
            :error nil
            :form {:name ""
                   :quantity "1"
                   :category ""
                   :expires ""
                   :expanded false
                   :error false}}})

(rf/reg-event-db
 ::initialize
 (fn [_ _]
   (init-db)))
