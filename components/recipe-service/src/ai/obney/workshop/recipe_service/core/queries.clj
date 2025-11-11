(ns ai.obney.workshop.recipe-service.core.queries
  "Query handlers for recipe-service.

   Provides recipe matching based on pantry inventory and
   recipe search capabilities."
  (:require [ai.obney.workshop.pantry-service.interface :as pantry]
            [ai.obney.grain.event-store-v2.interface :as es]
            [clojure.string :as str]
            [cognitect.anomalies :as anom]))

;;
;; Mock Recipe Data
;; TODO: Replace with actual recipe database or external API
;;

(def mock-recipes
  [{:id #uuid "550e8400-e29b-41d4-a716-446655440001"
    :title "Chicken Fried Rice"
    :time "25 min"
    :difficulty "Easy"
    :ingredients ["Rice" "Chicken Breast" "Eggs" "Onions" "Soy Sauce"]
    :instructions ["Cook rice" "Stir fry chicken" "Add eggs and vegetables" "Mix with soy sauce"]}
   {:id #uuid "550e8400-e29b-41d4-a716-446655440002"
    :title "Pasta Marinara"
    :time "20 min"
    :difficulty "Easy"
    :ingredients ["Pasta" "Tomatoes" "Olive Oil" "Garlic" "Basil"]
    :instructions ["Boil pasta" "Sauté garlic" "Add tomatoes" "Combine with pasta"]}
   {:id #uuid "550e8400-e29b-41d4-a716-446655440003"
    :title "Vegetable Stir Fry"
    :time "15 min"
    :difficulty "Easy"
    :ingredients ["Rice" "Onions" "Bell Peppers" "Soy Sauce"]
    :instructions ["Cook rice" "Stir fry vegetables" "Add sauce"]}
   {:id #uuid "550e8400-e29b-41d4-a716-446655440004"
    :title "Omelet"
    :time "10 min"
    :difficulty "Easy"
    :ingredients ["Eggs" "Milk" "Cheese"]
    :instructions ["Beat eggs with milk" "Cook in pan" "Add cheese"]}])

;;
;; Recipe Matching Logic
;;

(defn- normalize-name
  "Normalize ingredient/item names for matching"
  [name]
  (-> name
      (str/lower-case)
      (str/trim)))

(defn- calculate-match
  "Calculate match percentage and ingredient availability for a recipe"
  [recipe pantry-items]
  (let [recipe-ingredients (map normalize-name (:ingredients recipe))
        pantry-names (set (map (comp normalize-name :name) pantry-items))
        ingredients-with-status (mapv (fn [ing]
                                        {:name ing
                                         :have (contains? pantry-names (normalize-name ing))})
                                      (:ingredients recipe))
        have-count (count (filter :have ingredients-with-status))
        total-count (count ingredients-with-status)
        match-percent (if (zero? total-count)
                       0
                       (int (* 100 (/ have-count total-count))))]
    (assoc recipe
           :ingredients ingredients-with-status
           :match-percent match-percent
           :have-count have-count
           :total-count total-count)))

;;
;; Query Handlers
;;

(defn match-pantry
  "Find recipes that can be made with current pantry items"
  [{{:keys [household-id]} :query
    {:keys [user-id]} :auth-claims
    :keys [event-store]}]
  ;; TODO: Verify user is member of household
  (let [pantry-items (->> (es/read event-store {:types pantry/pantry-event-types
                                                 :tags #{[:household household-id]}})
                          (pantry/apply-pantry-events)
                          (vals))
        matched-recipes (mapv #(calculate-match % pantry-items) mock-recipes)
        sorted-recipes (sort-by :match-percent > matched-recipes)]
    {:query/result
     (mapv (fn [recipe]
             {:id (:id recipe)
              :title (:title recipe)
              :time (:time recipe)
              :difficulty (:difficulty recipe)
              :match-percent (:match-percent recipe)
              :ingredients (:ingredients recipe)})
           sorted-recipes)}))

(defn get-recipe-by-id
  "Get full recipe details by ID"
  [{{:keys [recipe-id]} :query
    {:keys [user-id]} :auth-claims
    :keys [event-store]}]
  (if-let [recipe (first (filter #(= (:id %) recipe-id) mock-recipes))]
    {:query/result
     {:id (:id recipe)
      :title (:title recipe)
      :time (:time recipe)
      :difficulty (:difficulty recipe)
      :instructions (:instructions recipe)
      :ingredients (mapv (fn [ing] {:name ing :amount "As needed"})
                        (:ingredients recipe))}}
    {::anom/category ::anom/not-found
     ::anom/message "Recipe not found"}))

(defn search-recipes
  "Search recipes by keyword"
  [{{:keys [query-text limit]} :query
    {:keys [user-id]} :auth-claims
    :keys [event-store]}]
  (let [search-term (normalize-name (or query-text ""))
        max-results (or limit 20)
        matching-recipes (filter #(str/includes?
                                   (normalize-name (:title %))
                                   search-term)
                                mock-recipes)]
    {:query/result
     (mapv (fn [recipe]
             {:id (:id recipe)
              :title (:title recipe)
              :description (str "A delicious " (:difficulty recipe) " recipe")})
           (take max-results matching-recipes))}))

;;
;; Query Registry
;;

(def queries
  {:recipes/match-pantry {:handler-fn #'match-pantry}
   :recipes/get-by-id {:handler-fn #'get-recipe-by-id}
   :recipes/search {:handler-fn #'search-recipes}})
