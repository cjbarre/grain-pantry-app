(ns ai.obney.workshop.ai-agent-service.core.commands
  "Command handlers for AI agent operations"
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.workshop.ai-agent-service.core.behavior-trees :as trees]
            [ai.obney.workshop.ai-agent-service.core.read-models :as rm]
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

;;
;; Command Registry
;;

(def commands
  "Command registry for AI agent service"
  {:ai/ask {:handler-fn #'ask-ai}})
