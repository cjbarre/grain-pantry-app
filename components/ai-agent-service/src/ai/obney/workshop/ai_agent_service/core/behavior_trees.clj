(ns ai.obney.workshop.ai-agent-service.core.behavior-trees
  "Behavior tree definitions for AI agents"
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt
             :refer [st-memory-has-value? lt-memory-has-value?]]
            [ai.obney.grain.behavior-tree-v2-dspy-extensions.interface :refer [dspy]]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.time.interface :as time]
            [ai.obney.workshop.ai-agent-service.core.signatures :as sigs]
            [ai.obney.workshop.ai-agent-service.core.schemas :as schemas]
            [ai.obney.workshop.pantry-service.interface :as pantry]
            [clojure.walk :as walk]))

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
   - Available categories"
  [{:keys [event-store st-memory auth-claims]}]
  (let [household-id (:household-id auth-claims)

        ;; Query pantry events with household tag filter
        pantry-items (->> (es/read event-store
                            {:types pantry/pantry-event-types
                             :tags #{[:household household-id]}})
                         (pantry/apply-pantry-events)
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
      {:items (stringify-uuids pantry-items)
       :expiring-soon (stringify-uuids expiring-soon)
       :categories categories})

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
