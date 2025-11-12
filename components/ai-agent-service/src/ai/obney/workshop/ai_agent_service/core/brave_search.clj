(ns ai.obney.workshop.ai-agent-service.core.brave-search
  "Brave Search API client for recipe discovery."
  (:require [clj-http.client :as http]
            [clojure.string :as str]
            [com.brunobonacci.mulog :as μ]))

(def brave-search-endpoint
  "https://api.search.brave.com/res/v1/web/search")

(defn search
  "Execute a web search using Brave Search API.

   Parameters:
   - query: Search query string
   - api-key: Brave Search API key
   - opts: Optional map with:
     - :count (int): Number of results (default 10, max 20)
     - :offset (int): Pagination offset (default 0)
     - :safesearch (string): off/moderate/strict (default moderate)

   Returns map with:
   - :web {:results [{:title, :description, :url}]}
   - :query {:original}

   Throws exception if API call fails."
  ([query api-key]
   (search query api-key {}))

  ([query api-key {qty :count
                   :keys [offset safesearch]
                   :or {qty 10
                        offset 0
                        safesearch "moderate"}}]
   (try
     (μ/log ::brave-search-query :query query :offset offset)
     (let [query-params (cond-> {"q" query
                                 "count" (str qty)
                                 "safesearch" safesearch}
                          ;; Only include offset if non-zero
                          (pos? offset) (assoc "offset" (str offset)))

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

(defn search-recipes
  "Search for recipes using Brave Search API.

   Constructs a recipe-specific search query from ingredients.

   Parameters:
   - ingredients: Vector of ingredient names (strings)
   - api-key: Brave Search API key
   - opts: Optional search options (see `search` function)

   Returns Brave Search API response."
  ([ingredients api-key]
   (search-recipes ingredients api-key {}))

  ([ingredients api-key opts]
   (let [ingredient-str (str/join ", " (take 5 ingredients))
         query (str "recipes using " ingredient-str)]
     (search query api-key opts))))
