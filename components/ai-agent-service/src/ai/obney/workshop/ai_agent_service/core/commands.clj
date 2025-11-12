(ns ai.obney.workshop.ai-agent-service.core.commands
  "Command handlers for AI agent operations"
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.workshop.ai-agent-service.core.behavior-trees :as trees]
            [ai.obney.workshop.ai-agent-service.core.read-models :as rm]
            [config.core :refer [env]]
            [cognitect.anomalies :as anom]))

(defn ask-ai
  "Process user question through AI copilot.

   Command: {:question \"What's in my pantry?\"}
   Auth claims: {:household-id <uuid> :user-id <uuid>}

   Returns: {:command/result {:response \"...\" :suggested-actions [...]}}
            or anomaly on failure"
  [{{:keys [question]} :command
    auth-claims :auth-claims
    event-store :event-store}]

  (try
    (let [household-id (:household-id auth-claims)
          user-id (:user-id auth-claims)]

      (when-not household-id
        (throw (ex-info "No household-id in auth claims" {:auth-claims auth-claims})))

      (when-not user-id
        (throw (ex-info "No user-id in auth claims" {:auth-claims auth-claims})))

      ;; Build behavior tree with event-sourced conversation history
      (let [bt (bt/build trees/pantry-copilot-tree
                         {:event-store event-store
                          :auth-claims auth-claims
                          :st-memory {:question question}
                          :read-model-fn #'rm/conversation-read-model
                          ;; Query conversation history events for this household/user
                          :queries [{:types #{:ai/question-asked
                                              :ai/response-generated}
                                     :tags #{[:household household-id] [:user user-id]}}]})

            ;; Execute tree with question in short-term memory
            result (bt/run bt)]

        (if (= result bt/success)
          ;; Success: extract response from behavior tree memory
          {:command/result
           {:response (-> bt :context :st-memory deref :response)
            :suggested-actions (-> bt :context :st-memory deref :suggested_actions)}}

          ;; Failure: tree execution failed
          {::anom/category ::anom/fault
           ::anom/message "AI processing failed"
           ::anom/result result})))

    (catch Exception e
      ;; Exception during execution
      {::anom/category ::anom/fault
       ::anom/message (str "AI error: " (.getMessage e))
       ::anom/ex-data (ex-data e)})))

(defn search-recipes
  "Search for recipes using Brave Search API and AI parsing.

   Command: {}
   Auth claims: {:household-id <uuid> :user-id <uuid>}

   Returns: {:command/result {:recipes [...] :reasoning \"...\"}}
            or anomaly on failure"
  [{auth-claims :auth-claims
    event-store :event-store
    :as context}]

  (try
    (let [household-id (:household-id auth-claims)]

      (when-not household-id
        (throw (ex-info "No household-id in auth claims" {:auth-claims auth-claims})))

      ;; Build behavior tree
      (let [brave-api-key (:brave-search-api-key env)

            _ (when-not brave-api-key
                (throw (ex-info "Brave Search API key not found in config.edn. Set :brave-search-api-key" {})))

            bt (bt/build trees/recipe-search-tree
                         {:event-store event-store
                          :auth-claims auth-claims
                          :st-memory {}
                          :config {:brave-api-key brave-api-key}
                          :context context})

            ;; Execute tree
            result (bt/run bt)]

        (if (= result bt/success)
          ;; Success: extract recipes from behavior tree memory
          (let [recipes (-> bt :context :st-memory deref :recipes)]
            {:command/result
             {:recipes recipes
              :reasoning (str "Found " (count recipes) " recipes based on your pantry ingredients")}})

          ;; Failure: tree execution failed
          {::anom/category ::anom/fault
           ::anom/message "Recipe search failed"
           ::anom/result result})))

    (catch Exception e
      ;; Exception during execution
      {::anom/category ::anom/fault
       ::anom/message (str "Recipe search error: " (.getMessage e))
       ::anom/ex-data (ex-data e)})))

(defn track-recipe-interaction
  "Track user interaction with a recipe.

   Command: {:recipe-id \"...\" :recipe-title \"...\"}
   Auth claims: {:household-id <uuid> :user-id <uuid>}

   Emits event based on interaction type."
  [interaction-type
   {{:keys [recipe-id recipe-title]} :command
    {:keys [household-id user-id]} :auth-claims
    :keys [event-store]}]

  (try
    (when-not (and household-id user-id)
      (throw (ex-info "Missing auth claims" {:household-id household-id :user-id user-id})))

    (when-not recipe-id
      (throw (ex-info "Missing recipe-id" {:command :recipe-id})))

    ;; Emit interaction event
    (es/append event-store
      {:events [(es/->event
                  {:type interaction-type
                   :tags #{[:household household-id] [:user user-id]}
                   :body {:recipe-id recipe-id
                          :recipe-title recipe-title
                          :household-id household-id
                          :user-id user-id}})]})

    {:command/result {:success true}}

    (catch Exception e
      {::anom/category ::anom/fault
       ::anom/message (str "Failed to track interaction: " (.getMessage e))
       ::anom/ex-data (ex-data e)})))

;;
;; Command Registry
;;

(def commands
  "Command registry for AI agent service"
  {:ai/ask {:handler-fn #'ask-ai}

   :ai/search-recipes {:handler-fn #'search-recipes}

   :ai/track-recipe-view {:handler-fn (partial track-recipe-interaction :ai/recipe-viewed)}

   :ai/track-recipe-dismiss {:handler-fn (partial track-recipe-interaction :ai/recipe-dismissed)}

   :ai/mark-recipe-cooked {:handler-fn (partial track-recipe-interaction :ai/recipe-marked-cooked)}})
