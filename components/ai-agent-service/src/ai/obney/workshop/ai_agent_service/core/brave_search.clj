(ns ai.obney.workshop.ai-agent-service.core.brave-search
  "Brave Search API client for recipe discovery."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [com.brunobonacci.mulog :as μ]))

(def brave-search-endpoint
  "https://api.search.brave.com/res/v1/web/search")

(def query-templates
  "Pool of query templates for search diversity."
  ["recipes using %s"
   "%s recipe ideas"
   "easy %s recipes"
   "what to cook with %s"
   "healthy %s dishes"
   "creative ways to use %s"])

(def cuisine-keywords
  "Cuisine types for search diversification."
  ["italian" "asian" "mexican" "mediterranean" "indian"
   "thai" "french" "middle eastern" "japanese" "american"
   "korean" "vietnamese" "greek" "moroccan"])

(def cooking-contexts
  "Cooking method and meal type keywords."
  ["quick dinner" "one pot" "slow cooker" "sheet pan"
   "meal prep" "healthy" "comfort food" "weeknight"
   "30 minute" "easy weeknight" "family friendly"])

(def spam-exclusions
  "Sites to exclude from search results."
  ["-pinterest" "-buzzfeed" "-tasty"])

(defn search
  "Execute a web search using Brave Search API.

   Parameters:
   - query: Search query string
   - api-key: Brave Search API key
   - opts: Optional map with:
     - :count (int): Number of results (default 10, max 20)
     - :offset (int): Pagination offset (default 0)
     - :safesearch (string): off/moderate/strict (default moderate)
     - :freshness (string): pd/pw/pm/py or date range (e.g. '2022-04-01to2022-07-30')

   Returns map with:
   - :web {:results [{:title, :description, :url}]}
   - :query {:original}

   Throws exception if API call fails."
  ([query api-key]
   (search query api-key {}))

  ([query api-key {qty :count
                   :keys [offset safesearch freshness]
                   :or {qty 10
                        offset 0
                        safesearch "moderate"}}]
   (try
     (μ/log ::brave-search-query :query query :offset offset :freshness freshness)
     (let [query-params (cond-> {"q" query
                                 "count" (str qty)
                                 "safesearch" safesearch}
                          ;; Only include offset if non-zero
                          (pos? offset) (assoc "offset" (str offset))
                          ;; Only include freshness if provided
                          freshness (assoc "freshness" freshness))

           response (http/get brave-search-endpoint
                              {:headers {"Accept" "application/json"
                                         "X-Subscription-Token" api-key}
                               :query-params query-params
                               :as :json
                               :throw-exceptions true})]
       (μ/log ::brave-search-success
              :results-count (count (get-in response [:body :web :results]))
              :offset offset)
       (:body response))
     (catch Exception e
       (μ/log ::brave-search-error :exception e :query query :offset offset)
       (throw (ex-info "Brave Search API failed"
                       {:query query
                        :offset offset
                        :error (.getMessage e)}
                       e))))))

(defn select-query-strategy
  "Select query strategy based on search iteration page.

   Strategy by page:
   - Page 0: Basic ingredient search (expiring items prioritized in caller)
   - Page 1: Cuisine-focused search with random cuisine
   - Page 2: Context-based search (meal type, cooking method)

   Returns map with :type and template/keywords for query building."
  [search-page]
  (case search-page
    0 {:type :basic
       :template (rand-nth query-templates)}

    1 {:type :cuisine
       :cuisine (rand-nth cuisine-keywords)
       :template "%s recipes with %s"}

    2 {:type :context
       :context (rand-nth cooking-contexts)
       :template "%s %s recipes"}

    ;; Fallback for unexpected page numbers
    {:type :basic
     :template "recipes using %s"}))

(defn build-search-query
  "Build search query with variation strategy and spam exclusions.

   Parameters:
   - ingredients: Vector of ingredient names (strings)
   - search-page: Search iteration number (0, 1, 2)

   Returns formatted search query string with spam site exclusions."
  [ingredients search-page]
  (let [strategy (select-query-strategy search-page)
        ingredient-str (str/join " " (take 5 ingredients))
        spam-str (str/join " " spam-exclusions)

        base-query (case (:type strategy)
                     :basic
                     (format (:template strategy) ingredient-str)

                     :cuisine
                     (format (:template strategy) (:cuisine strategy) ingredient-str)

                     :context
                     (format (:template strategy) (:context strategy) ingredient-str)

                     ;; Fallback
                     (str "recipes using " ingredient-str))]

    (μ/log ::query-strategy-selected
           :search-page search-page
           :strategy-type (:type strategy)
           :cuisine (:cuisine strategy)
           :context (:context strategy)
           :ingredients (take 5 ingredients))

    (str base-query " " spam-str)))

(defn search-recipes
  "Search for recipes using Brave Search API with query variation strategy.

   Constructs varied search queries based on search iteration to maximize diversity:
   - Page 0: Basic ingredient search
   - Page 1: Cuisine-focused search (random cuisine)
   - Page 2: Context-based search (meal type/method) + freshness filter

   Parameters:
   - ingredients: Vector of ingredient names (strings)
   - api-key: Brave Search API key
   - opts: Optional search options:
     - :count (int): Number of results (default 10, max 20)
     - :search-page (int): Search iteration number for query variation (default 0)
     - :safesearch (string): off/moderate/strict (default moderate)
     - Note: :freshness is automatically applied on page 2

   Returns Brave Search API response."
  ([ingredients api-key]
   (search-recipes ingredients api-key {}))

  ([ingredients api-key opts]
   (let [search-page (or (:search-page opts) 0)
         query (build-search-query ingredients search-page)

         ;; Apply freshness filter on page 2 for trending recipes
         enhanced-opts (cond-> opts
                         (= search-page 2) (assoc :freshness "pm")
                         ;; Remove search-page from opts (not a Brave API param)
                         true (dissoc :search-page))]

     (μ/log ::recipe-search-constructed
            :search-page search-page
            :query query
            :ingredients-count (count ingredients))

     (search query api-key enhanced-opts))))
