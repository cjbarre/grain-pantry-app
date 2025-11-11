(ns ai.obney.workshop.pantry-service.core.queries
  "Query handlers for pantry-service following the Grain pattern.

   Query functions receive context including :query, :auth-claims,
   and :event-store. Returns either a cognitect anomaly or a map
   with :query/result containing the requested data."
  (:require [ai.obney.workshop.pantry-service.core.read-models :as rm]
            [ai.obney.grain.event-store-v2.interface :as es]
            [clojure.string :as str]
            [cognitect.anomalies :as anom]))

;;
;; Pantry Queries
;;

(defn get-pantry-items
  [{{:keys [category search]} :query
    {:keys [household-id]} :auth-claims
    :keys [event-store]}]
  (let [all-items (->> (es/read event-store {:types rm/pantry-event-types})
                       (rm/apply-pantry-events)
                       (vals))]
    {:query/result
     (->> all-items
          (filter #(= (:household-id %) household-id))
          (filter #(or (nil? category)
                      (= (:category %) category)))
          (filter #(or (nil? search)
                      (str/includes? (str/lower-case (:name %))
                                    (str/lower-case search))))
          (mapv (fn [item]
                  {:id (:item-id item)
                   :name (:name item)
                   :quantity (:quantity item)
                   :category (:category item)
                   :expires (:expires item)}))
          (vec))}))

(defn get-expiring-soon
  [{{:keys [days]} :query
    {:keys [household-id]} :auth-claims
    :keys [event-store]}]
  (let [all-items (->> (es/read event-store {:types rm/pantry-event-types})
                       (rm/apply-pantry-events)
                       (vals))
        now (java.time.LocalDate/now)]
    {:query/result
     (->> all-items
          (filter #(= (:household-id %) household-id))
          (filter :expires)
          (map (fn [item]
                 (when-let [expires-str (:expires item)]
                   (try
                     (let [expires-date (java.time.LocalDate/parse expires-str)
                           days-until (.until now expires-date java.time.temporal.ChronoUnit/DAYS)]
                       (when (and (>= days-until 0)
                                 (<= days-until days))
                         {:id (:item-id item)
                          :name (:name item)
                          :expires expires-str
                          :days-until (int days-until)}))
                     (catch Exception _ nil)))))
          (remove nil?)
          (sort-by :days-until)
          (vec))}))

;;
;; Shopping List Queries
;;

(defn get-shopping-list
  [{_query :query
    {:keys [household-id]} :auth-claims
    :keys [event-store]}]
  (let [all-items (->> (es/read event-store {:types rm/shopping-event-types})
                       (rm/apply-shopping-events)
                       (vals))]
    {:query/result
     (->> all-items
          (filter #(= (:household-id %) household-id))
          (mapv (fn [item]
                  {:id (:item-id item)
                   :name (:name item)
                   :quantity (:quantity item)
                   :category (:category item)
                   :checked (:checked item)
                   :for-recipe (:for-recipe item)}))
          (vec))}))

;;
;; Household Queries
;;

(defn get-household
  [{_query :query
    {:keys [household-id]} :auth-claims
    :keys [event-store]}]
  (let [households (->> (es/read event-store {:types rm/household-event-types})
                        (rm/apply-household-events))
        household (get households household-id)]
    (if household
      {:query/result
       {:household-id household-id
        :household-name (:household-name household)
        :members (vec (:members household))}}
      {::anom/category ::anom/not-found
       ::anom/message "Household not found"})))

;;
;; Query Registry
;;

(def queries
  {:pantry/get-items {:handler-fn #'get-pantry-items}
   :pantry/get-expiring-soon {:handler-fn #'get-expiring-soon}
   :shopping/get-list {:handler-fn #'get-shopping-list}
   :pantry/get-household {:handler-fn #'get-household}})
