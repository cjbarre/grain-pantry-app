(ns ai.obney.workshop.pantry-service.core.commands
  "Command handlers for pantry-service following the Grain pattern.

   Each command handler receives context including :command, :auth-claims,
   and :event-store. Returns either a cognitect anomaly or a map with
   :command-result/events and optional result data."
  (:require [ai.obney.workshop.pantry-service.core.read-models :as rm]
            [ai.obney.grain.event-store-v2.interface :refer [->event] :as es]
            [cognitect.anomalies :as anom]))

;;
;; Household Commands
;;

(defn create-household
  [{{:keys [household-name]} :command
    {:keys [user-id]} :auth-claims}]
  (let [household-id (random-uuid)]
    {:command-result/events
     [(->event {:type :household/created
                :tags #{[:household household-id]}
                :body {:household-id household-id
                       :household-name household-name
                       :created-by user-id}})]
     :command/result {:household-id household-id}}))

;;
;; Pantry Item Commands
;;

(defn add-item
  [{{:keys [name quantity category expires]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  (let [item-id (random-uuid)]
    {:command-result/events
     [(->event {:type :pantry-item/added
                :tags #{[:household household-id] [:item item-id]}
                :body {:item-id item-id
                       :household-id household-id
                       :name name
                       :quantity quantity
                       :category category
                       :expires expires}})]
     :command/result {:item-id item-id}}))

(defn update-item
  [{{:keys [item-id quantity expires]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  {:command-result/events
   [(->event {:type :pantry-item/updated
              :tags #{[:household household-id] [:item item-id]}
              :body {:item-id item-id
                     :household-id household-id
                     :quantity quantity
                     :expires expires}})]
   :command/result {:success true}})

(defn remove-item
  [{{:keys [item-id]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  {:command-result/events
   [(->event {:type :pantry-item/removed
              :tags #{[:household household-id] [:item item-id]}
              :body {:item-id item-id
                     :household-id household-id}})]
   :command/result {:success true}})

;;
;; Shopping List Commands
;;

(defn add-shopping-item
  [{{:keys [name quantity category for-recipe]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  (let [item-id (random-uuid)]
    {:command-result/events
     [(->event {:type :shopping-item/added
                :tags #{[:household household-id] [:shopping-item item-id]}
                :body {:item-id item-id
                       :household-id household-id
                       :name name
                       :quantity quantity
                       :category category
                       :for-recipe for-recipe}})]
     :command/result {:item-id item-id}}))

(defn toggle-shopping-item
  [{{:keys [item-id checked]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  {:command-result/events
   [(->event {:type :shopping-item/checked
              :tags #{[:household household-id] [:shopping-item item-id]}
              :body {:item-id item-id
                     :household-id household-id
                     :checked checked}})]
   :command/result {:success true}})

(defn remove-shopping-item
  [{{:keys [item-id]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  {:command-result/events
   [(->event {:type :shopping-item/removed
              :tags #{[:household household-id] [:shopping-item item-id]}
              :body {:item-id item-id
                     :household-id household-id}})]
   :command/result {:success true}})

(defn clear-completed
  [{_command :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  ;; Get completed shopping items from read model
  (let [shopping-items (->> (es/read event-store {:types rm/shopping-event-types})
                            (rm/apply-shopping-events)
                            (vals)
                            (filter #(and (= (:household-id %) household-id)
                                        (:checked %))))
        item-ids (mapv :item-id shopping-items)]
    (if (empty? item-ids)
      {:command/result {:removed-count 0}}
      {:command-result/events
       [(->event {:type :shopping-items/cleared
                  :tags #{[:household household-id]}
                  :body {:household-id household-id
                         :item-ids item-ids}})]
       :command/result {:removed-count (count item-ids)}})))

(defn move-to-pantry
  [{{:keys [item-ids]} :command
    {:keys [user-id household-id]} :auth-claims
    :keys [event-store]}]
  ;; Get shopping items details from read model
  (let [shopping-items (->> (es/read event-store {:types rm/shopping-event-types})
                            (rm/apply-shopping-events)
                            (vals)
                            (filter #(and (= (:household-id %) household-id)
                                        (some #{(:item-id %)} item-ids))))
        ;; Create pantry-item/added events for each shopping item
        pantry-events (mapv (fn [item]
                              (->event {:type :pantry-item/added
                                       :tags #{[:household household-id]
                                              [:item (random-uuid)]}
                                       :body {:item-id (random-uuid)
                                              :household-id household-id
                                              :name (:name item)
                                              :quantity (:quantity item)
                                              :category (:category item)
                                              :expires nil}}))
                            shopping-items)
        ;; Create shopping-items/moved-to-pantry event
        moved-event (->event {:type :shopping-items/moved-to-pantry
                             :tags #{[:household household-id]}
                             :body {:household-id household-id
                                    :item-ids item-ids}})]
    (if (empty? shopping-items)
      {:command/result {:moved-count 0}}
      {:command-result/events (conj pantry-events moved-event)
       :command/result {:moved-count (count shopping-items)}})))

;;
;; Command Registry
;;

(def commands
  {:pantry/create-household {:handler-fn #'create-household}
   :pantry/add-item {:handler-fn #'add-item}
   :pantry/update-item {:handler-fn #'update-item}
   :pantry/remove-item {:handler-fn #'remove-item}
   :shopping/add-item {:handler-fn #'add-shopping-item}
   :shopping/toggle-item {:handler-fn #'toggle-shopping-item}
   :shopping/remove-item {:handler-fn #'remove-shopping-item}
   :shopping/clear-completed {:handler-fn #'clear-completed}
   :shopping/move-to-pantry {:handler-fn #'move-to-pantry}})
