(ns ai.obney.workshop.ai-agent-service.core.behavior-trees
  "Behavior tree definitions for AI agents"
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt
             :refer [st-memory-has-value? lt-memory-has-value?]]
            [ai.obney.grain.behavior-tree-v2-dspy-extensions.interface :refer [dspy]]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.time.interface :as time]
            [ai.obney.workshop.ai-agent-service.core.signatures :as sigs]
            [ai.obney.workshop.ai-agent-service.core.schemas :as schemas]
            [ai.obney.workshop.ai-agent-service.core.brave-search :as brave]
            [ai.obney.workshop.pantry-service.interface :as pantry]
            [clojure.string :as str]
            [clojure.walk :as walk]
            [com.brunobonacci.mulog :as μ]))

(defn stringify-uuids
  "Convert all UUID values in a data structure to strings for LLM consumption."
  [data]
  (walk/postwalk
   (fn [x]
     (if (uuid? x)
       (str x)
       x))
   data))

(defn load-pantry-context
  "Load current pantry state into behavior tree short-term memory.

   Queries the event store for pantry items and builds context including:
   - All pantry items for the household
   - Items expiring soon (within 3 days)
   - Available categories
   - Shopping list items"
  [{:keys [event-store st-memory auth-claims]}]
  (let [household-id (:household-id auth-claims)

        ;; Query pantry events with household tag filter
        pantry-items (->> (es/read event-store
                            {:types pantry/pantry-event-types
                             :tags #{[:household household-id]}})
                         (pantry/apply-pantry-events)
                         vals
                         vec)

        ;; Query shopping list events with household tag filter
        shopping-items (->> (es/read event-store
                              {:types pantry/shopping-event-types
                               :tags #{[:household household-id]}})
                           (pantry/apply-shopping-events)
                           vals
                           vec)

        ;; Calculate expiring soon items
        now (java.time.LocalDate/now)
        expiring-soon (->> pantry-items
                          (filter :expires)
                          (keep (fn [item]
                                  (try
                                    (let [expires (java.time.LocalDate/parse (:expires item))
                                          days (.until now expires java.time.temporal.ChronoUnit/DAYS)]
                                      (when (<= days 3)
                                        item))
                                    (catch Exception _ nil))))
                          vec)

        ;; Extract unique categories
        categories (->> pantry-items
                       (map :category)
                       distinct
                       vec)]

    ;; Store in short-term memory for LLM access (stringify UUIDs for JSON serialization)
    (swap! st-memory assoc :pantry_context
      {:current-date (str now)
       :items (stringify-uuids pantry-items)
       :expiring-soon (stringify-uuids expiring-soon)
       :categories categories
       :shopping-list (stringify-uuids shopping-items)})

    bt/success))

(defn persist-conversation
  "Save conversation exchange to event store.

   Persists both the user question and AI response as separate events
   with household and user tags for proper multi-tenant filtering."
  [{:keys [event-store st-memory auth-claims]}]
  (let [{:keys [question response suggested-actions]} @st-memory
        household-id (:household-id auth-claims)
        user-id (:user-id auth-claims)]

    ;; Append both question and response events
    (es/append event-store
      {:events [(es/->event
                  {:type :ai/question-asked
                   :tags #{[:household household-id] [:user user-id]}
                   :body {:question question
                          :household-id household-id
                          :user-id user-id}})
                (es/->event
                  {:type :ai/response-generated
                   :tags #{[:household household-id] [:user user-id]}
                   :body {:response response
                          :suggested-actions suggested-actions}})]})

    bt/success))

;;
;; Recipe Search Actions
;;

(defn initialize-recipe-search
  "Initialize accumulators for iterative recipe search.

   Sets up short-term memory for accumulating unique recipes across
   multiple search iterations.

   Note: previous_recipes starts as nil (not []) to signal to DSPy that
   no filtering is needed on first attempt."
  [{:keys [st-memory]}]
  (swap! st-memory assoc
         :filtered-recipes []
         :previous_recipes nil  ;; nil = no previous recipes yet
         :search-page 0)
  (μ/log ::recipe-search-initialized
         :filtered-recipes-count 0
         :previous-recipes nil
         :search-page 0)
  bt/success)

(defn search-brave-for-recipes
  "Search Brave Search API for recipes with varied query strategies.

   Strategy varies by search iteration:
   - Page 0: Basic ingredient search, prioritizes expiring items if available
   - Page 1: Cuisine-focused search with random cuisine
   - Page 2: Context-based search (meal type/method) with freshness filter

   Reads pantry context and search-page from short-term memory.
   Stores raw search results in short-term memory for DSPy processing."
  [{:keys [st-memory config]}]
  (try
    (let [pantry-items (get-in @st-memory [:pantry_context :items])
          expiring-soon (get-in @st-memory [:pantry_context :expiring-soon])
          search-page (or (:search-page @st-memory) 0)
          api-key (:brave-api-key config)]

      (when-not api-key
        (μ/log ::brave-api-key-missing :message "BRAVE_SEARCH_API_KEY not configured")
        (throw (ex-info "Brave Search API key not configured" {})))

      ;; On first search (page 0), prioritize expiring items if available
      ;; Otherwise use top 5 pantry items
      (let [ingredient-names (if (and (zero? search-page) (seq expiring-soon))
                              (mapv :name (take 3 expiring-soon))
                              (mapv :name (take 5 pantry-items)))

            prioritizing-expiring? (and (zero? search-page) (seq expiring-soon))

            _ (μ/log ::searching-recipes
                     :ingredients ingredient-names
                     :search-page search-page
                     :prioritizing-expiring prioritizing-expiring?)

            ;; Execute Brave Search with varied query strategy
            search-results (brave/search-recipes ingredient-names api-key
                                                {:count 10 :search-page search-page})]

        ;; Store results in short-term memory for DSPy action
        (swap! st-memory assoc :search_results search-results)
        (swap! st-memory assoc :pantry_items pantry-items)

        (μ/log ::recipes-found
               :count (count (get-in search-results [:web :results]))
               :search-page search-page
               :query (get-in search-results [:query :original]))
        bt/success))

    (catch Exception e
      (μ/log ::brave-search-failed :exception e)
      bt/failure)))

(defn keywordize-recipes
  "Convert recipe maps from string keys to keyword keys.

   DSPy returns recipes with string keys (due to Python/transit serialization),
   but ClojureScript expects keyword keys for destructuring.

   Transforms: {\"title\" \"Spanish Rice\"} → {:title \"Spanish Rice\"}"
  [{:keys [st-memory]}]
  (try
    (let [recipes (:recipes @st-memory)
          keywordized (mapv (fn [recipe]
                             (into {} (map (fn [[k v]]
                                            [(if (keyword? k) k (keyword k)) v])
                                          recipe)))
                           recipes)]
      (swap! st-memory assoc :recipes keywordized)
      (μ/log ::recipes-keywordized :count (count keywordized))
      bt/success)

    (catch Exception e
      (μ/log ::keywordize-failed :exception e)
      bt/failure)))

(defn enrich-recipe-ingredients
  "Cross-reference recipe ingredients with pantry items.

   Converts ingredient strings to maps with :have flag:
   'chicken' → {:name 'chicken' :have true/false}

   Also calculates :match-percent for each recipe based on
   how many ingredients the user already has.

   Recipes are sorted by match-percent (highest first)."
  [{:keys [st-memory]}]
  (try
    (let [recipes (:filtered-recipes @st-memory)
          pantry-items (get-in @st-memory [:pantry_context :items])
          reasoning (:reasoning_per_recipe @st-memory)

          ;; Build set of available ingredient names (lowercase for matching)
          available-set (into #{}
                              (comp
                               (map :name)
                               (map str/lower-case)
                               (map str/trim))
                              pantry-items)

          ;; Enrich each recipe
          enriched-recipes
          (mapv (fn [recipe]
                  (if-let [ingredients (:ingredients recipe)]
                    (let [enriched-ingredients
                          (mapv (fn [ing-name]
                                  (let [normalized (-> ing-name
                                                      str/lower-case
                                                      str/trim)
                                        have? (contains? available-set normalized)]
                                    {:name ing-name :have have?}))
                                ingredients)

                          ;; Calculate match percentage
                          have-count (count (filter :have enriched-ingredients))
                          total-count (count enriched-ingredients)
                          match-percent (if (pos? total-count)
                                         (int (* 100 (/ have-count total-count)))
                                         0)

                          ;; Add AI reasoning from DSPy output
                          recipe-id (:id recipe)
                          ai-reasoning (get reasoning recipe-id)]

                      (assoc recipe
                             :ingredients enriched-ingredients
                             :match-percent match-percent
                             :match-score match-percent
                             :ai-reasoning ai-reasoning))
                    recipe))
                recipes)

          ;; Sort by match-percent (highest first)
          sorted-recipes (vec (sort-by :match-percent > enriched-recipes))]

      (swap! st-memory assoc :filtered-recipes sorted-recipes)
      (μ/log ::ingredients-enriched
             :count (count sorted-recipes)
             :available-ingredients (count available-set))
      bt/success)

    (catch Exception e
      (μ/log ::enrichment-failed :exception e)
      bt/failure)))

(defn persist-recipe-suggestions
  "Persist recipe search and suggestion events to event store."
  [{:keys [event-store st-memory auth-claims]}]
  (try
    (let [recipes (:filtered-recipes @st-memory)
          search-results (:search_results @st-memory)
          household-id (:household-id auth-claims)
          query (get-in search-results [:query :original])
          results-count (count (get-in search-results [:web :results] []))]

      ;; Emit recipe search event
      (es/append event-store
        {:events (concat
                  ;; Search performed event
                  [(es/->event
                    {:type :ai/recipes-searched
                     :tags #{[:household household-id]}
                     :body {:query query
                            :results-count results-count
                            :household-id household-id}})]

                  ;; Individual recipe suggestion events
                  (mapv (fn [recipe]
                          (es/->event
                            {:type :ai/recipe-suggested
                             :tags #{[:household household-id]}
                             :body {:recipe-id (:id recipe)
                                    :recipe-title (:title recipe)
                                    :reasoning (:ai-reasoning recipe)
                                    :match-score (:match-score recipe)
                                    :household-id household-id}}))
                        recipes))})

      (μ/log ::recipes-persisted :count (count recipes))
      bt/success)

    (catch Exception e
      (μ/log ::persist-failed :exception e)
      bt/failure)))

(defn accumulate-recipes
  "Accumulate newly found recipes into filtered-recipes accumulator.

   Takes recipes from the current DSPy parsing and adds them to the
   filtered-recipes list. The DSPy signature handles semantic deduplication
   against previous_recipes, so we simply append the new recipes."
  [{:keys [st-memory]}]
  (try
    (let [new-recipes (:recipes @st-memory)
          existing-recipes (:filtered-recipes @st-memory)
          accumulated (vec (concat existing-recipes new-recipes))

          ;; Debug: log recipe IDs for tracking
          new-ids (mapv :id new-recipes)
          existing-ids (mapv :id existing-recipes)]

      (swap! st-memory assoc :filtered-recipes accumulated)
      (μ/log ::recipes-accumulated
             :new-count (count new-recipes)
             :existing-count (count existing-recipes)
             :total-count (count accumulated)
             :new-ids new-ids
             :existing-ids existing-ids)
      bt/success)

    (catch Exception e
      (μ/log ::accumulate-failed :exception e)
      bt/failure)))

(defn increment-search-page
  "Increment the search page counter for next Brave API call."
  [{:keys [st-memory]}]
  (swap! st-memory update :search-page (fnil inc 0))
  (μ/log ::search-page-incremented :page (:search-page @st-memory))
  bt/success)

(defn prepare-previous-recipes
  "Copy filtered-recipes into previous_recipes for DSPy input.

   DSPy reads inputs from short-term memory based on signature schema.
   This action ensures previous_recipes is available for semantic filtering.

   IMPORTANT: Recipes must be stringified (UUIDs converted to strings) for
   proper serialization to Python/DSPy.

   If no filtered recipes exist yet, keeps previous_recipes as nil."
  [{:keys [st-memory]}]
  (let [filtered (or (:filtered-recipes @st-memory) [])]
    (if (empty? filtered)
      ;; No recipes yet - keep previous_recipes as nil/empty
      (do
        (μ/log ::previous-recipes-prepared
               :count 0
               :status "no-recipes-yet")
        bt/success)
      ;; Have recipes - stringify and pass to DSPy
      (let [stringified (stringify-uuids filtered)]
        (swap! st-memory assoc :previous_recipes stringified)
        (μ/log ::previous-recipes-prepared
               :count (count stringified)
               :recipe-ids (mapv :id stringified)
               :status "recipes-prepared")
        bt/success))))

(defn has-enough-recipes?
  "Condition that checks if we have at least 6 diverse recipes.

   Returns success if filtered-recipes contains 6 or more recipes,
   failure otherwise (triggering next search iteration).

   Note: Target is 6 to ensure a good selection while avoiding
   unnecessary API calls. System will return whatever it finds
   after 3 search attempts, even if less than 6."
  [{:keys [st-memory]}]
  (let [recipe-count (count (:filtered-recipes @st-memory))]
    (μ/log ::checking-recipe-count :count recipe-count :target 6)
    (if (>= recipe-count 6)
      bt/success
      bt/failure)))

(def recipe-search-iteration-subtree
  "Single search iteration: search → parse → keywordize → accumulate.

   Designed to be run in a loop by search-recipes-until-enough action.
   Each iteration:
   1. Searches Brave API (query varies by search-page)
   2. Parses with DSPy RecipeSearcher (filters against previous_recipes)
   3. Converts string-keyed maps to keyword-keyed
   4. Accumulates to filtered-recipes list
   5. Increments search-page counter
   6. Copies filtered-recipes to previous_recipes for next iteration"
  [:sequence
   [:action search-brave-for-recipes]
   [:action {:id :recipe-parser
             :signature #'sigs/RecipeSearcher
             :operation :chain-of-thought}
    dspy]
   [:action keywordize-recipes]
   [:action accumulate-recipes]
   [:action increment-search-page]
   [:action prepare-previous-recipes]])

(defn search-recipes-until-enough
  "Run search iterations until 6+ recipes found or max attempts reached.

   Executes recipe-search-iteration-subtree in a loop, accumulating recipes
   across iterations until threshold met.

   The sub-tree shares event-store, auth-claims, config, and context from parent.
   State is threaded through as a map that gets converted to an atom for each iteration."
  [{:keys [st-memory event-store auth-claims config context]}]
  (loop [attempts 0
         accumulated-state @st-memory]

    (let [current-count (count (:filtered-recipes accumulated-state))
          max-attempts 5]

      (cond
        ;; Success: have enough recipes
        (>= current-count 6)
        (do
          (reset! st-memory accumulated-state)
          (μ/log ::search-loop-complete :attempts attempts :recipes current-count)
          bt/success)

        ;; Give up: hit max attempts
        (>= attempts max-attempts)
        (do
          (reset! st-memory accumulated-state)
          (μ/log ::search-loop-max-attempts :attempts attempts :recipes current-count)
          bt/success)

        ;; Continue: run another iteration
        :else
        (do
          (μ/log ::search-iteration :attempt (inc attempts) :current-count current-count)

          ;; Build sub-tree with accumulated state as MAP (converted to atom internally)
          (let [sub-bt (bt/build recipe-search-iteration-subtree
                                 {:event-store event-store
                                  :auth-claims auth-claims
                                  :st-memory accumulated-state
                                  :config config
                                  :context context})
                result (bt/run sub-bt)]

            (if (= result bt/success)
              ;; Extract updated state and continue loop
              (recur (inc attempts)
                     @(get-in sub-bt [:context :st-memory]))
              ;; Sub-tree failed, propagate failure
              (do
                (reset! st-memory accumulated-state)
                bt/failure))))))))

;;
;; Behavior Trees
;;

(def recipe-search-tree
  "Behavior tree for AI-powered recipe search with semantic diversity filtering.

   Flow:
   1. Initialize recipe search accumulators
   2. Load pantry context
   3. Loop search iterations until 6+ recipes or max attempts (5):
      Each iteration runs recipe-search-iteration-subtree:
      a. Search Brave API for recipes (varied query strategy per iteration)
      b. Parse with DSPy RecipeSearcher (semantic filtering against previous results)
      c. Keywordize recipe maps
      d. Accumulate unique recipes
      e. Increment search page for next iteration
      f. Prepare previous_recipes for next DSPy filtering
      Loop continues until 6+ recipes accumulated or 5 attempts exhausted.
   4. Enrich recipe ingredients with availability data (calculates match-percent and sorts by it)
   5. Persist suggestions to event store

   The DSPy RecipeSearcher handles RIGOROUS semantic deduplication:
   - Aggressively filters similar recipes (e.g., ALL chicken prep methods grouped as 'chicken')
   - Picks only the BEST recipe from each similarity group
   - Enforces maximum diversity: different cuisines, proteins, methods, dish types
   - Strictly avoids recipes similar to previous_recipes from earlier searches
   - Goal: Return recipes so different they couldn't be grouped together

   Recipes are ranked purely by ingredient match percentage (how many ingredients
   the user already has in their pantry)."
  [:sequence
   ;; 1. Initialize accumulators
   [:action initialize-recipe-search]

   ;; 2. Load pantry context
   [:action load-pantry-context]

   ;; 3. Loop search iterations until 6+ recipes or max attempts (5)
   [:action search-recipes-until-enough]

   ;; 4. Enrich ingredients with pantry availability (calculates and sorts by match-percent)
   [:action enrich-recipe-ingredients]

   ;; 5. Persist to event store
   [:action persist-recipe-suggestions]])

(def pantry-copilot-tree
  "Main behavior tree for pantry AI assistant.

   Flow:
   1. Verify we have a question to answer
   2. Load conversation history from event store (long-term memory)
   3. Load current pantry state into context
   4. Call LLM via DSPy with chain-of-thought reasoning
   5. Verify we got a response
   6. Persist conversation to event store"
  [:sequence
   ;; 1. Verify we have a question
   [:condition {:path [:question] :schema :string}
    st-memory-has-value?]

   ;; 2. Load conversation history from events (long-term memory)
   [:condition {:path [:conversation-history]
                :schema [:maybe ::schemas/conversation-history]}
    lt-memory-has-value?]

   ;; 3. Load current pantry state into context
   [:action load-pantry-context]

   ;; 4. Call LLM via DSPy (this populates :response and :suggested-actions)
   [:action {:id :copilot
             :signature #'sigs/PantryCopilot
             :operation :chain-of-thought}
    dspy]

   ;; 5. Verify we got a response
   [:condition {:path [:response] :schema :string}
    st-memory-has-value?]

   ;; 6. Persist to event store
   [:action persist-conversation]])
