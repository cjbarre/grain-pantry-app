# Building AI Features with Grain + DSPy

This guide provides a comprehensive walkthrough of building AI-powered features using Grain's behavior tree framework and DSPy for type-safe LLM interactions. It documents the architecture, patterns, and lessons learned from implementing the Phase 1 AI Copilot.

## Architecture Overview

AI features in this system combine several technologies:

- **Grain Behavior Trees**: Declarative AI agent orchestration with composable actions and conditions
- **DSPy**: Type-safe LLM interactions with Malli schema validation
- **Event Sourcing**: All AI interactions stored as immutable events for audit trail and replay
- **Polylith Components**: Clean separation between AI service logic and application concerns

### Key Concepts

**Behavior Trees**:
- **Sequence Nodes**: Execute children in order; fail if any child fails
- **Condition Nodes**: Check memory state (short-term or long-term)
- **Action Nodes**: Execute functions with side effects
- **Memory Types**:
  - **Short-term (st-memory)**: Temporary data for current execution (Clojure atom)
  - **Long-term (lt-memory)**: Event-sourced data rebuilt from event store via read models

**DSPy Signatures**:
- Define LLM input/output contracts using Malli schemas
- Support chain-of-thought reasoning for better responses
- Validate LLM inputs/outputs at runtime
- Enable type-safe Python/Clojure interop

**Event Sourcing for AI**:
- Every AI interaction stored as events (`:ai/question-asked`, `:ai/response-generated`)
- Conversation history rebuilt from events
- Full audit trail of AI decisions
- Multi-tenant isolation via tag-based filtering

## Component Structure

An AI service component follows this standard structure:

```
components/ai-agent-service/
├── deps.edn                                    # Dependencies (usually empty)
└── src/ai/obney/workshop/ai_agent_service/
    ├── interface.clj                           # Public API exports
    ├── interface/
    │   └── schemas.clj                         # Command/event/query schemas
    └── core/
        ├── config.clj                          # DSPy initialization
        ├── schemas.clj                         # Internal Malli schemas
        ├── signatures.clj                      # DSPy signature definitions
        ├── behavior_trees.clj                  # Behavior tree definitions
        ├── commands.clj                        # Command handlers
        ├── queries.clj                         # Query handlers (optional)
        └── read_models.clj                     # Event projections
```

### interface.clj (Public API)

Export only what other components need:

```clojure
(ns ai.obney.workshop.ai-agent-service.interface
  (:require [ai.obney.workshop.ai-agent-service.core.config :as config]
            [ai.obney.workshop.ai-agent-service.core.commands :as commands]))

;; Configuration
(def initialize-dspy! config/initialize-dspy!)
(def configured? config/configured?)

;; Command registry (merged into web-api)
(def commands commands/commands)
```

**Key Point**: Never expose behavior trees, DSPy signatures, or read models. These are internal implementation details.

### interface/schemas.clj (Grain Schema Registration)

Define command, event, and query schemas using Grain's `defschemas` macro:

```clojure
(ns ai.obney.workshop.ai-agent-service.interface.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas events
  {:ai/question-asked [:map
                       [:question :string]
                       [:household-id :uuid]
                       [:user-id :uuid]]
   :ai/response-generated [:map
                           [:response :string]
                           [:suggested-actions [:maybe [:vector :map]]]]})

(defschemas commands
  {:ai/ask [:map [:question :string]]})

(defschemas command-results
  {:ai/ask-result [:map
                   [:response :string]
                   [:suggested-actions [:maybe [:vector :map]]]]})
```

These schemas:
- Validate command payloads
- Define event structures for the event store
- Document API contracts
- Are automatically registered with Grain's central schema registry

## DSPy Integration

### Configuration (core/config.clj)

Initialize DSPy with OpenRouter or direct OpenAI:

```clojure
(ns ai.obney.workshop.ai-agent-service.core.config
  (:require [libpython-clj2.python :as py]
            [ai.obney.grain.clj-dspy.interface :as dspy]))

(defn initialize-dspy!
  "Initialize DSPy with LLM provider from environment variables.

   Supports:
   - OpenRouter: OPENROUTER_API_KEY + LLM_PROVIDER (e.g., 'openai/gpt-4o-mini')
   - Direct OpenAI: OPENAI_API_KEY + LLM_PROVIDER (e.g., 'gpt-4o-mini')

   Returns map with :provider and :api-base for confirmation."
  []
  (let [provider (or (System/getenv "LLM_PROVIDER") "openai/gpt-4o-mini")
        api-key (or (System/getenv "OPENROUTER_API_KEY")
                    (System/getenv "OPENAI_API_KEY"))
        api-base (or (System/getenv "OPENROUTER_API_BASE")
                     "https://openrouter.ai/api/v1")]

    (when-not api-key
      (throw (ex-info "No LLM API key found. Set OPENROUTER_API_KEY or OPENAI_API_KEY"
                      {:provider provider})))

    (let [lm (if (System/getenv "OPENROUTER_API_KEY")
               (dspy/LM provider
                        :api_key api-key
                        :api_base api-base
                        :cache false)
               (dspy/LM provider
                        :api_key api-key
                        :cache false))]
      (dspy/configure :lm lm)
      {:provider provider :api-base api-base})))

(defn configured? []
  "Check if DSPy has been initialized."
  (try
    (some? (py/get-attr (py/import-module "dspy") "settings"))
    (catch Exception _ false)))
```

**Best Practices**:
- Support multiple LLM providers via environment variables
- Disable cache for real-time responses
- Throw clear exceptions if API keys missing
- Provide health check function (`configured?`)

### Internal Schemas (core/schemas.clj)

Define Malli schemas for behavior tree memory and DSPy inputs/outputs:

```clojure
(ns ai.obney.workshop.ai-agent-service.core.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas agent-schemas
  {::conversation-history
   [:vector {:desc "Chat message history"}
    [:map [:role :string] [:content :string]]]

   ::pantry-context
   [:map {:desc "Current pantry state for AI context"}
    [:current-date :string]
    [:items [:vector :map]]
    [:expiring-soon [:vector :map]]
    [:categories [:vector :string]]]

   ::question
   [:string {:desc "User's current question"}]

   ::response
   [:string {:desc "AI assistant's response"}]

   ::suggested-actions
   [:maybe [:vector {:desc "Actions AI suggests user take"}
            [:map
             [:type :keyword]
             [:description :string]
             [:params :map]]]]})
```

**Key Points**:
- Use qualified keywords (`::schema-name`)
- Add `:desc` metadata for documentation
- Define schemas for both inputs (question, context) and outputs (response, actions)
- These schemas bridge Clojure and Python via DSPy

### DSPy Signatures (core/signatures.clj)

Define LLM interaction contracts with detailed prompts:

```clojure
(ns ai.obney.workshop.ai-agent-service.core.signatures
  (:require [ai.obney.grain.clj-dspy.interface :refer [defsignature]]
            [ai.obney.workshop.ai-agent-service.core.schemas :as schemas]))

(defsignature PantryCopilot
  "You are a helpful pantry management assistant. You help users:
   - Understand what's in their pantry
   - Find recipes they can make with available ingredients
   - Plan meals for the week
   - Manage shopping lists
   - Track expiring items and reduce food waste

   The pantry_context includes the current_date field (YYYY-MM-DD format).
   Use this date to calculate expiration timelines and suggest actions with appropriate dates.

   Be friendly, concise, and proactive in suggesting helpful actions.

   AVAILABLE ACTIONS (use these exact action types):

   PANTRY ACTIONS:
   - add-pantry-item: Add an item to the user's pantry
     params: {\"name\" string, \"quantity\" string, \"category\" string, \"expires\" string|null}
     example: {\"type\" \"add-pantry-item\", \"description\" \"Add milk to your pantry\",
               \"params\" {\"name\" \"Milk\", \"quantity\" \"1 gallon\", \"category\" \"Dairy\", \"expires\" \"2025-12-15\"}}

   - remove-pantry-item: Remove an item from the pantry
     params: {\"item-id\" string}
     example: {\"type\" \"remove-pantry-item\", \"description\" \"Remove expired milk\",
               \"params\" {\"item-id\" \"abc-123\"}}

   SHOPPING LIST ACTIONS:
   - add-shopping-item: Add an item to the shopping list
     params: {\"name\" string, \"quantity\" string, \"category\" string, \"for-recipe\" string|null}
     example: {\"type\" \"add-shopping-item\", \"description\" \"Add bacon for carbonara recipe\",
               \"params\" {\"name\" \"Bacon\", \"quantity\" \"1 lb\", \"category\" \"Meat\", \"for-recipe\" \"Pasta Carbonara\"}}

   When suggesting actions:
   - Provide clear descriptions explaining WHY the action is helpful
   - Use exact category names from pantry_context when available
   - For expiration dates, calculate from current_date
   - Use YYYY-MM-DD format for all dates
   - Only suggest 1-3 most relevant actions per response
   - Make actions specific and immediately actionable"

  {:inputs {:conversation_history ::schemas/conversation-history
            :pantry_context ::schemas/pantry-context
            :question ::schemas/question}

   :outputs {:response ::schemas/response
             :suggested_actions ::schemas/suggested-actions}})
```

**Design Best Practices**:

1. **Detailed Prompts**: Include role description, capabilities, and behavioral guidelines
2. **Structured Outputs**: Provide JSON schemas with examples for executable actions
3. **Context Awareness**: Document what context fields mean (e.g., `current_date` for expiration calculations)
4. **Schema Mapping**: Use `::schemas/...` qualified keywords that reference Malli schemas
5. **Action Schemas**: Be very specific about action structure - the LLM needs clear examples

**How It Works**:
1. `defsignature` macro converts Malli schemas to DSPy types
2. DSPy validates LLM inputs before the API call
3. LLM generates response following the prompt structure
4. DSPy validates LLM outputs against the schema
5. Type violations are caught and can trigger retries

## Behavior Tree Patterns

### Main Tree Definition

```clojure
(ns ai.obney.workshop.ai-agent-service.core.behavior-trees
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt
             :refer [st-memory-has-value? lt-memory-has-value?]]
            [ai.obney.grain.behavior-tree-v2-dspy-extensions.interface :refer [dspy]]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.workshop.ai-agent-service.core.signatures :as sigs]
            [ai.obney.workshop.pantry-service.interface :as pantry]))

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
```

**Node Types Explained**:

- **[:sequence ...]**: Runs children in order; fails if any child fails
- **[:condition {...} predicate-fn]**: Checks condition; succeeds if predicate returns truthy
- **[:action fn-or-map]**: Executes function with context; must return `bt/success` or `bt/failure`

**Memory Access**:
- Short-term memory checked via `st-memory-has-value?`
- Long-term memory checked via `lt-memory-has-value?` (rebuilt from events)

### Writing Custom Actions

**Template**:

```clojure
(defn my-action
  "Docstring explaining what this action does and why."
  [{:keys [event-store st-memory lt-memory auth-claims config]}]
  (try
    ;; 1. Read from context
    (let [data-from-st-memory @st-memory
          data-from-lt-memory @lt-memory
          user-id (:user-id auth-claims)
          household-id (:household-id auth-claims)]

      ;; 2. Perform logic (query events, calculate, etc.)
      (let [result (do-something data-from-st-memory)]

        ;; 3. Update short-term memory (for downstream nodes)
        (swap! st-memory assoc :result-key result)

        ;; 4. Optionally persist events
        (es/append event-store
          {:events [(es/->event
                      {:type :my/event-happened
                       :tags #{[:household household-id] [:user user-id]}
                       :body {:data result}})]})

        ;; 5. Return success
        bt/success))

    (catch Exception e
      (println "Error in my-action:" (.getMessage e))
      bt/failure)))
```

**Best Practices**:
- Always document what the action does
- Use destructuring for clean code
- Handle exceptions and return `bt/failure`
- Tag events for multi-tenancy (household/user)
- Keep actions focused (single responsibility)
- Use `swap!` for atom updates (short-term memory)

### Example: Load Pantry Context

```clojure
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
   - Current date (for expiration calculations)"
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

    ;; Store in short-term memory for LLM access
    ;; (stringify UUIDs for JSON serialization to Python/LLM)
    (swap! st-memory assoc :pantry_context
      {:current-date (str now)
       :items (stringify-uuids pantry-items)
       :expiring-soon (stringify-uuids expiring-soon)
       :categories categories})

    bt/success))
```

**Key Patterns**:
- **Tag-based filtering**: `#{[:household household-id]}` ensures proper multi-tenancy
- **Read model application**: `apply-pantry-events` rebuilds current state from events
- **UUID serialization**: `stringify-uuids` converts UUIDs to strings for LLM JSON compatibility
- **Temporal calculations**: Using Java time API for expiration logic
- **Memory update**: `swap! st-memory assoc` makes context available to DSPy action

### Example: Persist Conversation

```clojure
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
```

**Key Patterns**:
- **Dual event emission**: Question and response stored as separate events
- **Tag propagation**: Both events tagged with household + user for filtering
- **Suggested actions persisted**: Enables future analysis of AI action suggestions

### Chain-of-Thought Execution

Enable reasoning by using `:operation :chain-of-thought` in the DSPy action:

```clojure
[:action {:id :copilot
          :signature #'sigs/PantryCopilot
          :operation :chain-of-thought}  ; ← Enables internal reasoning
 dspy]
```

**What it does**:
1. DSPy prompts LLM to generate internal reasoning before the answer
2. Improves response quality for complex queries
3. Uses more tokens but provides better accuracy

**Example flow**:
```
User: "What can I make for dinner?"
↓
[Chain-of-Thought Reasoning - Internal]
"The user has eggs, milk, flour, and bacon. Milk expires in 3 days.
 They could make pancakes (uses expiring milk + eggs + flour) or
 an omelette (uses eggs + milk). Pancakes would use more expiring items.
 I should suggest pancakes and offer to add bacon to make it heartier."
↓
Response: "You can make pancakes! You have all the ingredients (eggs,
          milk, flour) and your milk expires in 3 days, so it's perfect
          timing. Want to add bacon for a heartier breakfast?"
Actions: [{type: "add-shopping-item", description: "Add bacon", ...}]
```

## Command Handler Integration

Wire the behavior tree to a command handler:

```clojure
(ns ai.obney.workshop.ai-agent-service.core.commands
  (:require [ai.obney.grain.behavior-tree-v2.interface :as bt]
            [ai.obney.workshop.ai-agent-service.core.behavior-trees :as trees]
            [ai.obney.workshop.ai-agent-service.core.read-models :as rm]))

(defn ask-ai
  "Handle :ai/ask command by executing the pantry copilot behavior tree.

   Flow:
   1. Build behavior tree with:
      - Short-term memory: {:question \"...\"}
      - Long-term memory: Conversation history from events
      - Read model: conversation-read-model
   2. Execute tree
   3. Return response and suggested actions from short-term memory"
  [{{:keys [question]} :command
    {:keys [household-id user-id]} :auth-claims
    :keys [event-store] :as context}]

  (let [;; Build behavior tree with conversation history
        bt (bt/build trees/pantry-copilot-tree
             {:event-store event-store
              :st-memory {:question question}
              :read-model-fn #'rm/conversation-read-model
              :queries [{:types #{:ai/question-asked :ai/response-generated}
                         :tags #{[:household household-id] [:user user-id]}}]
              :config {}
              :context context})

        ;; Execute tree
        result (bt/run bt)
        st-memory (:st-memory result)]

    (if (= (:status result) :success)
      ;; Success: Return response and actions
      {:command/result {:response (:response st-memory)
                        :suggested-actions (:suggested-actions st-memory)}}

      ;; Failure: Return error
      {:command/result {:error "Failed to generate AI response"
                        :status (:status result)}})))

;; Command registry
(def commands
  {:ai/ask {:handler-fn #'ask-ai}})
```

**Key Patterns**:
- **Behavior tree building**: Use `bt/build` with queries for long-term memory
- **Read model function**: Transforms events into conversation history
- **Result extraction**: Get response from `:st-memory` after execution
- **Error handling**: Check `:status` and return appropriate result

### Conversation Read Model

```clojure
(ns ai.obney.workshop.ai-agent-service.core.read-models)

(defn conversation-read-model
  "Build conversation history from AI events.

   Reduces events into a vector of {:role, :content} messages."
  [initial-state events]
  (reduce
    (fn [acc event]
      (case (:event/type event)
        :ai/question-asked
        (update acc :conversation-history (fnil conj [])
          {:role "user"
           :content (:question (:event/body event))})

        :ai/response-generated
        (update acc :conversation-history (fnil conj [])
          {:role "assistant"
           :content (:response (:event/body event))})

        acc))
    initial-state
    events))
```

**How it works**:
1. Behavior tree queries events matching `:types` and `:tags`
2. Read model reduces events into state (conversation history)
3. Result stored in long-term memory (`:lt-memory`)
4. DSPy action reads from `lt-memory` via `:conversation_history` input

**Why this is elegant**:
- Behavior tree handles event querying automatically
- Read model is a pure function (events → state)
- Long-term memory rebuilt fresh on each execution
- No manual event store queries in actions

## Frontend Integration

### Re-frame Effect Pattern

**Effect Handler** (store/ai/effects.cljs):

```clojure
(ns store.ai.effects
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [components.api.anomaly :refer [anomaly?]]))

(rf/reg-fx
  ::ask-ai
  (fn [{:keys [question api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client
                           {:command/name :ai/ask
                            :question question}))]
        (if (anomaly? response)
          (rf/dispatch (conj on-failure response))
          (rf/dispatch (conj on-success response)))))))
```

**Event Handlers** (store/ai/events.cljs):

```clojure
(ns store.ai.events
  (:require [re-frame.core :as rf]
            [store.ai.effects :as ai-fx]))

;; Ask AI event
(rf/reg-event-fx
  ::ask-ai
  (fn [{:keys [db]} [_ question]]
    {:db (-> db
             ;; Optimistic update: add user message immediately
             (update-in [:ai :conversation] (fnil conj [])
               {:role "user" :content question})
             ;; Set loading state
             (assoc-in [:ai :loading] true))

     ;; Trigger API call effect
     ::ai-fx/ask-ai {:question question
                     :api-client (:api/client db)
                     :on-success [::ask-ai-success]
                     :on-failure [::ask-ai-failure]}}))

;; Success handler
(rf/reg-event-db
  ::ask-ai-success
  (fn [db [_ response]]
    (-> db
        ;; Add assistant message with suggested actions
        (update-in [:ai :conversation] (fnil conj [])
          {:role "assistant"
           :content (:response response)
           :suggested-actions (:suggested-actions response)})
        ;; Clear loading state
        (assoc-in [:ai :loading] false))))

;; Failure handler
(rf/reg-event-db
  ::ask-ai-failure
  (fn [db [_ error]]
    (-> db
        ;; Show error message in chat
        (update-in [:ai :conversation] (fnil conj [])
          {:role "system"
           :content (str "Error: " (:message error "Failed to get response"))})
        ;; Clear loading state
        (assoc-in [:ai :loading] false))))
```

**Key Patterns**:
- **Effects are pure side-effect handlers**: Never touch db, only dispatch events
- **Optimistic updates**: Add user message immediately for better UX
- **Loading states**: Prevent duplicate requests
- **Error handling**: Show errors as system messages in chat

### Executable AI Actions

**Action Normalization** (store/ai/events.cljs):

```clojure
(defn- normalize-action
  "Convert DSPy action (string keys) to Clojure action (keyword keys)."
  [action]
  (let [params (get action "params" (:params action))
        normalized-params (into {} (map (fn [[k v]]
                                         [(if (string? k) (keyword k) k) v])
                                       params))]
    {:type (keyword (get action "type" (:type action)))
     :description (get action "description" (:description action))
     :params normalized-params}))

(rf/reg-event-fx
  ::execute-suggested-action
  (fn [{:keys [db]} [_ action]]
    (let [api-client (:api/client db)
          normalized (normalize-action action)
          action-type (:type normalized)]
      (case action-type
        :add-pantry-item
        {:dispatch [::pantry-events/add-pantry-item
                   (:params normalized)
                   api-client
                   #(rf/dispatch [::action-success normalized])
                   #(rf/dispatch [::action-failure normalized %])]}

        :add-shopping-item
        {:dispatch [::pantry-events/add-shopping-item
                   (:params normalized)
                   api-client
                   #(rf/dispatch [::action-success normalized])
                   #(rf/dispatch [::action-failure normalized %])]}

        ;; Default: unknown action
        {:db (update-in db [:ai :conversation] (fnil conj [])
               {:role "system"
                :content (str "❌ Unknown action type: " action-type)})}))))

(rf/reg-event-fx
  ::action-success
  (fn [{:keys [db]} [_ action]]
    (let [api-client (:api/client db)]
      {:db (update-in db [:ai :conversation] (fnil conj [])
             {:role "system"
              :content (str "✓ " (:description action))})
       ;; Refresh data after action
       :dispatch-n [[::pantry-events/fetch-pantry-items api-client]
                    [::pantry-events/fetch-shopping-list api-client]]})))
```

**Why normalization is needed**:
- DSPy returns JSON with string keys: `{"type": "add-pantry-item", ...}`
- Re-frame expects keyword keys: `{:type :add-pantry-item, ...}`
- Normalization bridges the gap

## Common Patterns & Best Practices

### UUID Serialization for LLMs

**Problem**: UUIDs don't serialize properly to JSON for Python/LLMs.

**Solution**: Use a walker function to convert all UUIDs to strings:

```clojure
(defn stringify-uuids
  "Convert all UUID values in a data structure to strings for LLM consumption."
  [data]
  (walk/postwalk
   (fn [x]
     (if (uuid? x)
       (str x)
       x))
   data))

;; Usage
(swap! st-memory assoc :pantry_context
  {:items (stringify-uuids pantry-items)  ; ← All UUIDs become strings
   ...})
```

### Event Sourcing for AI Conversations

**Pattern**: Store every AI interaction as events.

**Benefits**:
- Full audit trail of AI decisions
- Can replay conversations
- Analyze AI usage patterns
- Privacy: Can delete user events on request
- Debugging: Trace exact AI responses

**Query Example**:

```clojure
;; Get all AI interactions for a user
(->> (es/read event-store
       {:types #{:ai/question-asked :ai/response-generated}
        :tags #{[:user user-id]}})
     (mapv :event/body))
```

### Polylith Interface/Core Separation

**Rule**: Only expose what other components need through `interface.clj`.

**AI Agent Service Exports**:
- ✅ `initialize-dspy!` - Startup function
- ✅ `commands` - Command registry
- ✅ `configured?` - Health check
- ❌ NOT behavior trees (internal)
- ❌ NOT DSPy signatures (internal)
- ❌ NOT read models (internal)

**Why**:
- Clear component boundaries
- Easy to refactor internals
- Polylith catches violations

## Troubleshooting & Common Pitfalls

### 1. Polylith Dependency Violations

**Error**: "Illegal dependency on namespace X.core.Y in component Z"

**Cause**: Trying to import another component's `core` namespace directly.

**Fix**: Export needed functions through the component's `interface.clj`:

```clojure
;; ❌ WRONG
(ns ai-agent-service.core.behavior-trees
  (:require [pantry-service.core.read-models :as rm]))

;; ✅ CORRECT
(ns ai-agent-service.core.behavior-trees
  (:require [pantry-service.interface :as pantry]))

;; In pantry-service/interface.clj:
(def pantry-event-types rm/pantry-event-types)
(def apply-pantry-events rm/apply-pantry-events)
```

### 2. Re-frame Initialization Order

**Error**: "No subscription handler registered for ::ai-subs/conversation"

**Cause**: Sidebar rendered before subscription namespaces loaded.

**Fix**: Explicitly require subscription/event namespaces in app/core.cljs:

```clojure
(ns app.core
  (:require [store.ai.subs]     ; ← Force subscription registration
            [store.ai.events]    ; ← Force event registration
            [store.ai.effects]   ; ← Force effect registration
            [components.ai.sidebar]))
```

### 3. UUID Serialization Problems

**Error**: LLM receives `{"item-id": {"$uuid": "..."}}` instead of string.

**Fix**: Use `stringify-uuids` before passing data to DSPy:

```clojure
(swap! st-memory assoc :pantry_context
  {:items (stringify-uuids pantry-items)})
```

### 4. DSPy Action Schema Design

**Problem**: AI returns unstructured actions: "You should add milk".

**Fix**: Provide detailed JSON examples in DSPy signature prompt:

```clojure
"AVAILABLE ACTIONS:
- add-pantry-item:
  example: {\"type\" \"add-pantry-item\",
            \"description\" \"Add milk to your pantry\",
            \"params\" {\"name\" \"Milk\", \"quantity\" \"1 gallon\"}}"
```

The more specific the schema and examples, the better the LLM performs.

### 5. Conversation History Loading

**Problem**: Need to pass conversation history to DSPy - how?

**Wrong**: Query events manually in custom action.

**Right**: Use behavior tree long-term memory with read models:

```clojure
(let [bt (bt/build tree
           {:st-memory {:question question}
            :read-model-fn #'conversation-read-model  ; ← Reduces events to history
            :queries [{:types #{:ai/question-asked :ai/response-generated}
                       :tags #{[:household household-id]}}]})]
  (bt/run bt))
```

Behavior tree queries events and applies read model automatically.

## Quick Reference

### File Locations Checklist

**AI Service Component**:
- `components/ai-agent-service/src/.../interface.clj` - Public API
- `components/ai-agent-service/src/.../interface/schemas.clj` - Commands/events
- `components/ai-agent-service/src/.../core/config.clj` - DSPy init
- `components/ai-agent-service/src/.../core/schemas.clj` - Internal Malli schemas
- `components/ai-agent-service/src/.../core/signatures.clj` - DSPy signatures
- `components/ai-agent-service/src/.../core/behavior_trees.clj` - Behavior trees
- `components/ai-agent-service/src/.../core/commands.clj` - Command handlers
- `components/ai-agent-service/src/.../core/read_models.clj` - Event projections

**Frontend AI Integration**:
- `ui/web-app/src/components/ai/sidebar.cljs` - UI component
- `ui/web-app/src/store/ai/events.cljs` - Re-frame events
- `ui/web-app/src/store/ai/effects.cljs` - Re-frame effects
- `ui/web-app/src/store/ai/subs.cljs` - Re-frame subscriptions

### Environment Variables

```bash
# LLM Provider (OpenRouter recommended)
OPENROUTER_API_KEY=your_key_here
LLM_PROVIDER=openai/gpt-4o-mini
OPENROUTER_API_BASE=https://openrouter.ai/api/v1

# Or direct OpenAI
OPENAI_API_KEY=your_key_here
LLM_PROVIDER=gpt-4o-mini
```

### REPL Testing Commands

```clojure
;; Initialize DSPy
(require '[ai.obney.workshop.ai-agent-service.interface :as ai])
(ai/initialize-dspy!)
(ai/configured?)  ; => true

;; Test behavior tree
(require '[ai.obney.workshop.ai-agent-service.core.commands :as ai-cmd])
(ai-cmd/ask-ai
  {:command {:question "What's in my pantry?"}
   :auth-claims {:household-id <uuid> :user-id <uuid>}
   :event-store event-store})
```

### Creating New AI Components

Use the Babashka script to scaffold a new component:

```bash
bb scripts/create_component.bb my-ai-service
```

Then add AI-specific files:
- `core/config.clj` - DSPy initialization
- `core/schemas.clj` - Malli schemas for agent state
- `core/signatures.clj` - DSPy signature definitions
- `core/behavior_trees.clj` - Behavior tree definitions

## Summary

Building AI features with Grain + DSPy follows this pattern:

1. **Define DSPy signature** with clear prompts and schemas
2. **Create behavior tree** with custom actions (load context, persist results)
3. **Write command handler** that builds and runs the tree
4. **Export via interface.clj** (commands, initialization)
5. **Wire to web-api** by merging command registry
6. **Build frontend** with re-frame effects/events/subs
7. **Create UI components** with subscriptions

The combination of behavior trees (orchestration), DSPy (type-safe LLM calls), and event sourcing (audit trail) provides a robust foundation for AI features that are testable, composable, and production-ready.
