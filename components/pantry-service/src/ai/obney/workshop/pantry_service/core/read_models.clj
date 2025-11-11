(ns ai.obney.workshop.pantry-service.core.read-models
  "Read model projections for pantry-service.

   Builds in-memory state from events for households, pantry items,
   and shopping lists. Used by commands, queries, and processors."
  (:require [clojure.set :as set]
            [com.brunobonacci.mulog :as u]))

;;
;; Event Type Definitions
;;

(def household-event-types
  #{:household/created
    :household/member-added})

(def pantry-event-types
  #{:pantry-item/added
    :pantry-item/updated
    :pantry-item/removed
    :shopping-items/moved-to-pantry})

(def shopping-event-types
  #{:shopping-item/added
    :shopping-item/checked
    :shopping-item/removed
    :shopping-items/cleared
    :shopping-items/moved-to-pantry})

(def all-event-types
  (set/union household-event-types
             pantry-event-types
             shopping-event-types))

;;
;; Household Read Model
;;

(defmulti apply-household-event
  (fn [_state event]
    (:event/type event)))

(defmethod apply-household-event :household/created
  [state {:keys [household-id household-name created-by]}]
  (assoc state household-id
         {:household-name household-name
          :members #{created-by}}))

(defmethod apply-household-event :household/member-added
  [state {:keys [household-id user-id]}]
  (update-in state [household-id :members] (fnil conj #{}) user-id))

(defmethod apply-household-event :default
  [state _event]
  state)

(defn apply-household-events
  [events]
  (reduce
   (fn [state event]
     (apply-household-event state event))
   {}
   events))

;;
;; Pantry Items Read Model
;;

(defmulti apply-pantry-event
  (fn [_state event]
    (:event/type event)))

(defmethod apply-pantry-event :pantry-item/added
  [state {:keys [item-id household-id name quantity category expires]}]
  (assoc state item-id
         {:item-id item-id
          :household-id household-id
          :name name
          :quantity quantity
          :category category
          :expires expires}))

(defmethod apply-pantry-event :pantry-item/updated
  [state {:keys [item-id quantity expires]}]
  (-> state
      (cond-> quantity (assoc-in [item-id :quantity] quantity))
      (cond-> expires (assoc-in [item-id :expires] expires))))

(defmethod apply-pantry-event :pantry-item/removed
  [state {:keys [item-id]}]
  (dissoc state item-id))

(defmethod apply-pantry-event :default
  [state _event]
  state)

(defn apply-pantry-events
  [events]
  (reduce
   (fn [state event]
     (apply-pantry-event state event))
   {}
   events))

;;
;; Shopping Items Read Model
;;

(defmulti apply-shopping-event
  (fn [_state event]
    (:event/type event)))

(defmethod apply-shopping-event :shopping-item/added
  [state {:keys [item-id household-id name quantity category for-recipe]}]
  (assoc state item-id
         {:item-id item-id
          :household-id household-id
          :name name
          :quantity quantity
          :category category
          :checked false
          :for-recipe for-recipe}))

(defmethod apply-shopping-event :shopping-item/checked
  [state {:keys [item-id checked]}]
  (assoc-in state [item-id :checked] checked))

(defmethod apply-shopping-event :shopping-item/removed
  [state {:keys [item-id]}]
  (dissoc state item-id))

(defmethod apply-shopping-event :shopping-items/cleared
  [state {:keys [item-ids]}]
  (reduce dissoc state item-ids))

(defmethod apply-shopping-event :shopping-items/moved-to-pantry
  [state {:keys [item-ids]}]
  ;; Remove items from shopping list when moved to pantry
  (reduce dissoc state item-ids))

(defmethod apply-shopping-event :default
  [state _event]
  state)

(defn apply-shopping-events
  [events]
  (reduce
   (fn [state event]
     (apply-shopping-event state event))
   {}
   events))
