# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Architecture Overview

This is a **Polylith monorepo** that combines Clojure backend services with a ClojureScript/React frontend. The architecture uses:

- **Grain Framework**: Event-sourced microservices framework built on top of Polylith
- **Polylith Architecture**: Components in `components/`, bases in `bases/`, and projects in `projects/`
- **Event Sourcing**: All state changes are captured as events in an event store
- **CQRS Pattern**: Separate command and query handlers
- **Python Integration**: Uses libpython-clj2 for Python interop (DSPy for LLM workflows)

### Key Directories

- `components/`: Reusable Clojure components (foundation components and service components)
  - Foundation components: `crypto`, `crypto-kms`, `email`, `email-ses`, `file-store`, `file-store-s3`, `jwt`, `url-presigner`, `url-presigner-aws`
  - Service components: `user-service`, `pantry-service`, `recipe-service`
- `bases/`: Application entry points (`web-api`)
- `projects/`: Deployable artifacts (currently just `web-api`)
- `ui/web-app/`: ClojureScript frontend using UIx, shadow-cljs, and shadcn components
- `development/`: Development-only code and REPL utilities

### Grain Service Components

Grain service components follow a specific structure:
- `interface.clj`: Public API for the component
- `interface/schemas.clj`: Command/event/query schemas using `defschemas` macro
- `core.clj`: Implementation logic
- Each service defines: `commands`, `queries`, and `todo-processors` (event handlers)

The top-level namespace is `ai.obney.workshop` (configured in workspace.edn).

## Development Workflow

### Backend Development

1. Start infrastructure:
   ```bash
   docker-compose up -d
   ```
   This starts PostgreSQL (pgvector) and LocalStack (for S3/KMS emulation).

2. Start REPL with dev alias:
   ```bash
   clj -A:dev
   ```

3. In the REPL, evaluate `development/src/repl_stuff.clj` and run the `do` form on line 10:
   ```clojure
   (do
     (def service (service/start))
     (def context (::service/context service))
     (def event-store (:event-store context)))
   ```

4. Stop the service:
   ```clojure
   (service/stop service)
   ```

### Frontend Development

The frontend is a shadow-cljs app with UIx (React wrapper) and shadcn UI components.

1. Install dependencies:
   ```bash
   cd ui/web-app && npm install
   ```

2. Start dev server (runs shadow-cljs, Tailwind CSS watch, and TypeScript watch):
   ```bash
   npm run dev
   ```
   This starts the dev server on http://localhost:8080 with hot reload.

3. Build for production:
   ```bash
   npm run build
   ```
   This compiles TypeScript shadcn components, builds CSS, and compiles ClojureScript with advanced optimizations.

4. Build variants (with different API base URLs):
   - `npm run build:dev` - Points to http://localhost:8081
   - `npm run build:staging` - Points to staging API
   - `npm run build:prod` - Points to production API

### Creating New Components

Use the Babashka script to create a new Grain service component:

```bash
bb scripts/create_component.bb <component-name>
```

Then add the component to the root `deps.edn` file under the `:dev` alias.

## LocalStack Configuration

LocalStack emulates AWS services (S3, KMS) for local development. Configuration is in `config.edn`:

```clojure
{:localstack/enabled true
 :localstack/endpoint "http://localhost:4566"
 :aws/region "us-east-1"
 :kms-key-id "alias/grain-local-key"
 :s3-bucket "grain-files"}
```

When LocalStack mode is enabled, emails are logged via mulog instead of being sent through SES.

See LOCALSTACK.md for detailed setup and testing instructions.

## Python Environment

The project uses Python for LLM-related functionality (DSPy).

### Setup with UV

1. Install venv:
   ```bash
   uv venv --python 3.13 .venv
   ```

2. Activate venv:
   ```bash
   source .venv/bin/activate
   ```

3. Install dependencies:
   ```bash
   uv pip install -r requirements.txt
   ```

## System Architecture

The web-api base (`bases/web-api/core.clj`) defines the system configuration using Integrant:

- **Event Store**: Can be configured as `:in-memory` or `:postgres`
- **Event Pubsub**: Uses core.async for event distribution
- **Todo Processors**: Event handlers that react to domain events
- **Context**: Shared context containing event-store, command/query registries, email client, crypto provider, JWT secret
- **Webserver**: Pedestal HTTP server with CORS support
- **Authentication**: Uses JWT tokens stored in HTTP-only cookies

### Request Flow

1. HTTP requests enter through Pedestal interceptors
2. Auth cookie is extracted and validated (`extract-auth-cookie` interceptor)
3. Commands are routed through `command-request-handler`
4. Queries are routed through `query-request-handler`
5. Auth tokens are set/cleared via the `set-auth-cookie` interceptor

## Key Technologies

- **Clojure 1.12.0**: Backend language
- **shadow-cljs**: ClojureScript build tool
- **UIx**: React wrapper for ClojureScript
- **shadcn/ui**: UI component library (TypeScript components wrapped for ClojureScript)
- **Tailwind CSS v4**: Styling
- **Pedestal**: HTTP server framework
- **Integrant**: System lifecycle management
- **libpython-clj2**: Python interop
- **DSPy**: LLM workflow framework
- **mulog**: Structured logging with CloudWatch EMF publisher support

## Configuration

Configuration is loaded from `config.edn` using the `config.core/env` var. Key configuration:

- `:webserver/http-port`: HTTP port for web server
- `:jwt-secret`: Secret for signing JWT tokens
- `:app-base`: Base URL for the application
- `:email-from`: From address for emails
- `:openrouter-api-key`: API key for OpenRouter (used with DSPy)

## Polylith Commands

The project uses Polylith for workspace management:

```bash
# Run Polylith CLI
clj -A:poly <command>
```

Common commands:
- `clj -A:poly info`: Show workspace info
- `clj -A:poly check`: Check workspace validity
- `clj -A:poly test`: Run tests

## Notes

- The workspace top namespace is `ai.obney.workshop`
- The default profile is `development` (alias `dev`)
- All Grain service components expose their APIs through `interface.clj` files
- The interface namespace convention is `<namespace>.interface`
- Schemas are defined using the Grain `defschemas` macro for central registration

## Building Features End-to-End

This section provides a comprehensive guide to building features in this Grain/Polylith-based system, from backend to frontend.

### Backend Service Component Structure

A typical Grain service component has this structure:

```
components/my-service/
├── deps.edn
└── src/ai/obney/workshop/my_service/
    ├── interface.clj                    # Public API exports
    ├── interface/
    │   └── schemas.clj                  # Malli schemas
    └── core/
        ├── commands.clj                 # Command handlers
        ├── queries.clj                  # Query handlers
        ├── read_models.clj              # Event projections
        └── todo_processors.clj          # Async event handlers
```

#### Commands

Commands are write operations that emit events. Located in `core/commands.clj`:

```clojure
(defn add-item
  [{{:keys [name quantity category]} :command
    {:keys [household-id]} :auth-claims
    :keys [event-store]}]
  (let [item-id (random-uuid)]
    {:command-result/events
     [(->event {:type :pantry-item/added
                :tags #{[:household household-id] [:item item-id]}
                :body {:item-id item-id
                       :household-id household-id
                       :name name
                       :quantity quantity
                       :category category}})]
     :command/result {:item-id item-id}}))

;; Registry exported via interface.clj
(def commands
  {:pantry/add-item {:handler-fn #'add-item}})
```

**Key points:**
- Commands receive context with `:command` (payload), `:auth-claims` (JWT data), `:event-store`
- Return events via `->event` helper (not direct state mutations)
- Events have `:type` (keyword), `:tags` (for filtering), `:body` (data)
- Optional `:command/result` for immediate response data
- Return Cognitect anomalies for errors: `{::anom/category ::anom/conflict ::anom/message "..."}`

#### Events and Schemas

Events are defined using Malli schemas in `interface/schemas.clj`:

```clojure
(ns ai.obney.workshop.pantry-service.interface.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas commands
  {:pantry/add-item [:map
                     [:name :string]
                     [:quantity :string]
                     [:category :string]]})

(defschemas events
  {:pantry-item/added [:map
                       [:item-id :uuid]
                       [:household-id :uuid]
                       [:name :string]
                       [:quantity :string]
                       [:category :string]]})

(defschemas queries
  {:pantry/get-items [:map
                      [:category [:maybe :string]]]})
```

#### Queries

Queries are read operations that rebuild state from events. Located in `core/queries.clj`:

```clojure
(defn get-pantry-items
  [{{:keys [category]} :query
    {:keys [household-id]} :auth-claims
    :keys [event-store]}]
  (let [all-items (->> (es/read event-store {:types rm/pantry-event-types})
                       (rm/apply-pantry-events)
                       (vals))]
    {:query/result
     (->> all-items
          (filter #(= (:household-id %) household-id))
          (filter #(or (nil? category) (= (:category %) category)))
          (mapv #(select-keys % [:item-id :name :quantity :category])))}))

(def queries
  {:pantry/get-items {:handler-fn #'get-pantry-items}})
```

**Key points:**
- Queries read events from event store
- Apply read models to rebuild current state
- Can filter by auth-claims for authorization
- Return `:query/result` with data

#### Read Models (Event Projections)

Read models transform event streams into queryable state in `core/read_models.clj`:

```clojure
(def pantry-event-types
  #{:pantry-item/added :pantry-item/updated :pantry-item/removed})

(defmulti apply-pantry-event
  (fn [_state event] (:event/type event)))

(defmethod apply-pantry-event :pantry-item/added
  [state {:keys [item-id household-id name quantity category]}]
  (assoc state item-id
         {:item-id item-id
          :household-id household-id
          :name name
          :quantity quantity
          :category category}))

(defmethod apply-pantry-event :pantry-item/removed
  [state {:keys [item-id]}]
  (dissoc state item-id))

(defn apply-pantry-events
  [events]
  (reduce
   (fn [state event]
     (apply-pantry-event state event))
   {}
   events))
```

#### Todo Processors (Async Event Handlers)

Todo processors subscribe to events and react asynchronously in `core/todo_processors.clj`:

```clojure
(defn create-household-for-new-user
  [{{:keys [user-id household-id]} :event
    :keys [event-store] :as context}]
  {:result/events
   [(->event {:type :household/created
              :tags #{[:household household-id]}
              :body {:household-id household-id
                     :created-by user-id}})]})

(def todo-processors
  {:pantry/create-household
   {:handler-fn #'create-household-for-new-user
    :topics [:user/signed-up]}})    ;; Subscribe to this event type
```

**Key points:**
- Subscribe to event types via `:topics`
- Can emit new events or trigger commands
- Run asynchronously via pub/sub
- Useful for cross-service coordination

#### Service Interface

The `interface.clj` file exports all public APIs:

```clojure
(ns ai.obney.workshop.pantry-service.interface
  (:require [ai.obney.workshop.pantry-service.core.commands :as commands]
            [ai.obney.workshop.pantry-service.core.queries :as queries]
            [ai.obney.workshop.pantry-service.core.todo-processors :as tp]))

(def commands commands/commands)
(def queries queries/queries)
(def todo-processors tp/todo-processors)
```

### Wiring Components in web-api Base

The `bases/web-api/src/ai/obney/workshop/web_api/core.clj` file wires everything together using Integrant:

```clojure
(def system
  {::context {:event-store (ig/ref ::event-store)
              :command-registry (merge user-service/commands
                                       pantry-service/commands)
              :query-registry (merge user-service/queries
                                     pantry-service/queries)
              :jwt-secret (:jwt-secret env)}

   ::todo-processors {:event-pubsub (ig/ref ::event-pubsub)
                      :context (ig/ref ::context)}

   ::routes {:context (ig/ref ::context)}

   ::webserver {::http/routes (ig/ref ::routes)}})
```

**Important:** The Grain framework auto-generates `/command` and `/query` endpoints by reading the registries. No manual routing needed!

#### Authentication Flow

```clojure
;; Extract JWT from cookie
(def extract-auth-cookie
  (interceptor/interceptor
   {:name ::extract-auth-cookie
    :enter
    (fn [context]
      (let [token (get-in context [:request :cookies "auth-token" :value])
            payload (jwt/unsign {:token token :secret jwt-secret})]
        (cond-> context
          payload (assoc-in [:grain/additional-context :auth-claims] payload))))}))

;; Set JWT cookie on login
(def set-auth-cookie
  (interceptor/interceptor
   {:name ::set-auth-cookie
    :leave
    (fn [context]
      (if (and (= 200 (get-in context [:response :status]))
               (= :user/login (get-in context [:grain/command :command/name])))
        (assoc-in context [:response :cookies "auth-token"]
                  {:value (:jwt (get context :grain/command-result))
                   :http-only true
                   :same-site :lax
                   :path "/"})
        context))}))
```

### Frontend Integration

The frontend uses ClojureScript with UIx (React) and Re-frame (state management).

#### API Client

Located in `ui/web-app/src/components/api/core.cljs`:

```clojure
(defprotocol APIClient
  (command [this cmd])
  (query [this qry]))

(deftype RemoteAPIClient [config]
  APIClient
  (command [_ command]
    (http/post (str (:base-url config) "/command")
               {:transit-params (merge command
                                      {:command/id (random-uuid)
                                       :command/timestamp (js/Date.)})}))

  (query [_ query]
    (http/post (str (:base-url config) "/query")
               {:transit-params (merge query
                                      {:query/id (random-uuid)
                                       :query/timestamp (js/Date.)})})))
```

#### Re-frame Effects

Effects handle side effects like API calls in `store/{feature}/effects.cljs`:

```clojure
(rf/reg-fx
  ::add-pantry-item
  (fn [{:keys [item api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client
                                      {:command/name :pantry/add-item
                                       :name (:name item)
                                       :quantity (:quantity item)
                                       :category (:category item)}))]
        (if (anomaly? response)
          (rf/dispatch (conj on-failure response))
          (rf/dispatch (conj on-success response)))))))
```

#### Re-frame Events

Events orchestrate effects and state updates in `store/{feature}/events.cljs`:

```clojure
(rf/reg-event-fx
  ::add-pantry-item
  (fn [_ctx [_ item api-client on-success on-failure]]
    {::pantry-fx/add-pantry-item {:item item
                                   :api-client api-client
                                   :on-success [::add-pantry-item-success on-success]
                                   :on-failure [::add-pantry-item-failure on-failure]}}))

(rf/reg-event-fx
  ::add-pantry-item-success
  (fn [{:keys [db]} [_ on-success _response]]
    {:db db
     :dispatch [::fetch-pantry-items api-client]
     ::router-fx/navigate {:navigate-fn on-success}}))
```

#### Re-frame Subscriptions

Subscriptions provide reactive data to components in `store/{feature}/subs.cljs`:

```clojure
(rf/reg-sub
  ::pantry-items
  (fn [db _]
    (get-in db [:pantry :items] [])))

(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:pantry :loading] false)))
```

#### UI Components

Components use UIx with re-frame hooks:

```clojure
(defui view []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        pantry-items (use-subscribe [::pantry-subs/pantry-items])
        loading (use-subscribe [::pantry-subs/loading])

        _ (use-effect
            (fn []
              (rf/dispatch [::pantry-events/fetch-pantry-items api-client])
              (fn []))
            [api-client])]

    ($ :div
       (if loading
         ($ :p "Loading...")
         (for [item pantry-items]
           ($ :div {:key (:id item)} (:name item)))))))
```

### Complete Feature Flow Example

Here's a complete walkthrough of adding a pantry item:

1. **User Action**: User clicks "Add Item" button
2. **Component**: `(rf/dispatch [::pantry-events/add-pantry-item {...} api-client ...])`
3. **Event Handler**: Triggers effect
4. **Effect**: HTTP POST to `/command` with `{:command/name :pantry/add-item ...}`
5. **Backend Interceptor**: Extracts JWT → injects `:auth-claims`
6. **Command Processor**: Looks up `:pantry/add-item` in registry
7. **Command Handler**: `pantry-service.core.commands/add-item` executes
8. **Event Emission**: Returns `{:command-result/events [{:type :pantry-item/added ...}]}`
9. **Event Store**: Persists event to database
10. **Event Pub/Sub**: Publishes to subscribed todo-processors
11. **HTTP Response**: Returns `{:item-id ...}` to frontend
12. **Success Handler**: Dispatches `fetch-pantry-items` to refresh
13. **Query Executed**: `:pantry/get-items` reads events, rebuilds state
14. **UI Update**: Subscription triggers re-render with new data

### Creating a New Feature: Step-by-Step Guide

Example: Add "Mark Item as Favorite" feature

#### Step 1: Define Schemas

In `components/pantry-service/src/ai/obney/workshop/pantry_service/interface/schemas.clj`:

```clojure
;; Add to events
:pantry-item/marked-favorite [:map
                               [:item-id :uuid]
                               [:household-id :uuid]
                               [:favorite :boolean]]

;; Add to commands
:pantry/mark-favorite [:map
                       [:item-id :uuid]
                       [:favorite :boolean]]
```

#### Step 2: Add Command Handler

In `components/pantry-service/src/ai/obney/workshop/pantry_service/core/commands.clj`:

```clojure
(defn mark-favorite
  [{{:keys [item-id favorite]} :command
    {:keys [household-id]} :auth-claims}]
  {:command-result/events
   [(->event {:type :pantry-item/marked-favorite
              :tags #{[:household household-id] [:item item-id]}
              :body {:item-id item-id
                     :household-id household-id
                     :favorite favorite}})]})

;; Add to registry
(def commands
  {:pantry/add-item {:handler-fn #'add-item}
   :pantry/mark-favorite {:handler-fn #'mark-favorite}})
```

#### Step 3: Update Read Model

In `components/pantry-service/src/ai/obney/workshop/pantry_service/core/read_models.clj`:

```clojure
;; Add event type
(def pantry-event-types
  #{:pantry-item/added
    :pantry-item/updated
    :pantry-item/removed
    :pantry-item/marked-favorite})

;; Handle event
(defmethod apply-pantry-event :pantry-item/marked-favorite
  [state {:keys [item-id favorite]}]
  (assoc-in state [item-id :favorite] favorite))
```

#### Step 4: Frontend Effect

In `ui/web-app/src/store/pantry/effects.cljs`:

```clojure
(rf/reg-fx
  ::mark-favorite
  (fn [{:keys [item-id favorite api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client {:command/name :pantry/mark-favorite
                                                   :item-id item-id
                                                   :favorite favorite}))]
        (if (anomaly? response)
          (rf/dispatch (conj on-failure response))
          (rf/dispatch on-success))))))
```

#### Step 5: Frontend Event

In `ui/web-app/src/store/pantry/events.cljs`:

```clojure
(rf/reg-event-fx
  ::mark-favorite
  (fn [_ctx [_ item-id favorite api-client]]
    {::pantry-fx/mark-favorite {:item-id item-id
                                 :favorite favorite
                                 :api-client api-client
                                 :on-success [::mark-favorite-success api-client]
                                 :on-failure [::mark-favorite-failure]}}))

(rf/reg-event-fx
  ::mark-favorite-success
  (fn [{:keys [db]} [_ api-client]]
    {:db db
     :dispatch [::fetch-pantry-items api-client]}))
```

#### Step 6: UI Component

In `ui/web-app/src/components/pantry/pantry_list.cljs`:

```clojure
($ button/Button
   {:variant "ghost"
    :on-click #(rf/dispatch [::pantry-events/mark-favorite
                             (:id item)
                             (not (:favorite item))
                             api-client])}
   (if (:favorite item) "⭐" "☆"))
```

#### Step 7: Restart Backend

Commands/queries are merged at startup:

```bash
# In REPL
(service/stop app)
(def app (service/start))
```

**Done!** The Grain framework auto-generates the `/command` endpoint for `:pantry/mark-favorite`.

### Authentication & Authorization

Commands and queries automatically receive `:auth-claims` from JWT cookies:

```clojure
(defn protected-command
  [{{:keys [item-id]} :command
    {:keys [user-id household-id]} :auth-claims    ;; Auto-injected!
    :keys [event-store]}]
  (if-not household-id
    {::anom/category ::anom/forbidden
     ::anom/message "Authentication required"}
    ;; ... handle command
    ))
```

### Key Patterns & Best Practices

#### Event Sourcing
- Events are immutable facts
- Never delete events; emit compensating events instead
- Read models can be rebuilt from scratch at any time

#### CQRS
- Commands: Write operations that return events
- Queries: Read operations that rebuild state on-the-fly
- Separation allows independent scaling and optimization

#### Error Handling
Use Cognitect anomalies for errors:

```clojure
{::anom/category ::anom/conflict      ;; or ::anom/forbidden, ::anom/not-found
 ::anom/message "User-friendly message"}
```

#### Re-frame Flow
```
Component → Event → Effect → API Call → Success/Failure Event → DB Update → Subscription → UI Update
```

### Quick Reference: File Locations

**Backend:**
- `components/{service}/src/.../interface.clj` - Public API exports
- `components/{service}/src/.../interface/schemas.clj` - Malli schemas
- `components/{service}/src/.../core/commands.clj` - Command handlers
- `components/{service}/src/.../core/queries.clj` - Query handlers
- `components/{service}/src/.../core/read_models.clj` - Event projections
- `components/{service}/src/.../core/todo_processors.clj` - Async event handlers
- `bases/web-api/src/.../web_api/core.clj` - Wiring layer (Integrant)

**Frontend:**
- `ui/web-app/src/components/api/core.cljs` - API client
- `ui/web-app/src/store/{feature}/events.cljs` - Re-frame events
- `ui/web-app/src/store/{feature}/effects.cljs` - Re-frame effects
- `ui/web-app/src/store/{feature}/subs.cljs` - Re-frame subscriptions
- `ui/web-app/src/components/{feature}/core.cljs` - UI components

## Building AI Features with Grain + DSPy

This section provides a comprehensive guide to building AI-powered features using Grain's behavior tree framework and DSPy for type-safe LLM interactions. It documents the architecture, patterns, and lessons learned from implementing the Phase 1 AI Copilot.

### Architecture Overview

AI features in this system combine several technologies:

- **Grain Behavior Trees**: Declarative AI agent orchestration with composable actions and conditions
- **DSPy**: Type-safe LLM interactions with Malli schema validation
- **Event Sourcing**: All AI interactions stored as immutable events for audit trail and replay
- **Polylith Components**: Clean separation between AI service logic and application concerns

#### Key Concepts

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

### Component Structure

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

#### interface.clj (Public API)

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

#### interface/schemas.clj (Grain Schema Registration)

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

### DSPy Integration

#### Configuration (core/config.clj)

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

#### Internal Schemas (core/schemas.clj)

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

#### DSPy Signatures (core/signatures.clj)

Define LLM interaction contracts:

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

### Behavior Tree Patterns

#### Main Tree Definition

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

#### Writing Custom Actions

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

#### Example: Load Pantry Context

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

#### Example: Persist Conversation

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

#### Chain-of-Thought Execution

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

### Command Handler Integration

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

#### Conversation Read Model

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

### Frontend Integration

#### Re-frame Effect Pattern

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

**Subscriptions** (store/ai/subs.cljs):

```clojure
(ns store.ai.subs
  (:require [re-frame.core :as rf]))

(rf/reg-sub
  ::conversation
  (fn [db _]
    (get-in db [:ai :conversation] [])))

(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:ai :loading] false)))
```

**Key Patterns**:
- **Effects are pure side-effect handlers**: Never touch db, only dispatch events
- **Optimistic updates**: Add user message immediately for better UX
- **Loading states**: Prevent duplicate requests
- **Error handling**: Show errors as system messages in chat
- **Path-based subscriptions**: React components re-render on changes

#### Executable AI Actions

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

#### UI Component (components/ai/sidebar.cljs)

```clojure
(ns components.ai.sidebar
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-ref]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["marked" :as marked]
            [store.ai.subs :as ai-subs]
            [store.ai.events :as ai-events]))

(defui suggested-action-button
  [{:keys [action executing? executed?]}]
  ($ button/Button
     {:on-click #(rf/dispatch [::ai-events/execute-suggested-action action])
      :disabled (or executing? executed?)
      :variant "outline"
      :size "sm"
      :class "mt-2 w-full justify-start text-left h-auto min-h-[2rem] whitespace-normal"}
     ($ :div {:class "flex items-start gap-2 w-full py-1"}
        (when executed?
          ($ :span {:class "shrink-0 mt-0.5"} "✓ "))
        ($ :span {:class "flex-1 break-words"}
           (get action "description" (:description action)))
        (when executing?
          ($ :span {:class "shrink-0 animate-pulse"} "...")))))

(defui chat-message
  [{:keys [role content suggested-actions]}]
  (let [rendered-html (when (= role "assistant")
                        (marked/parse content #js {:breaks true :gfm true}))]
    (cond
      ;; System messages (action feedback) - centered
      (= role "system")
      ($ :div {:class "flex justify-center mb-3"}
         ($ :div {:class "text-sm text-muted-foreground px-3 py-1 border border-border bg-muted max-w-[85%]"}
            content))

      ;; User/assistant messages
      :else
      ($ :div {:class (str "flex gap-3 mb-3 "
                          (if (= role "user") "justify-end" "justify-start"))}
         ;; Avatar + message bubble
         ;; ... (see full implementation in sidebar.cljs)

         ;; Suggested actions (only for assistant messages)
         (when (and (= role "assistant") (seq suggested-actions))
           ($ :div {:class "mt-3 flex flex-col gap-2"}
              (for [[idx action] (map-indexed vector suggested-actions)]
                ($ suggested-action-button
                   {:key idx
                    :action action
                    :executing? false
                    :executed? false}))))))))

(defui ai-sidebar
  [{:keys [collapsed on-toggle]}]
  (let [conversation (use-subscribe [::ai-subs/conversation])
        loading (use-subscribe [::ai-subs/loading])
        [input-value set-input-value] (use-state "")
        messages-end-ref (use-ref nil)

        handle-send (fn []
                     (when (and (not-empty input-value) (not loading))
                       (rf/dispatch [::ai-events/ask-ai input-value])
                       (set-input-value "")))

        ;; Auto-scroll to bottom on new messages
        _ (use-effect
            (fn []
              (when @messages-end-ref
                (.scrollIntoView @messages-end-ref #js {:behavior "smooth"})))
            [(count conversation)])]

    ;; ... (render collapsed or expanded state)
    ))
```

**Key Features**:
- **Markdown rendering**: Using `marked` library for rich AI responses
- **Action buttons**: Execute AI suggestions with one click
- **Auto-scroll**: Smooth scroll to bottom on new messages
- **Loading states**: Disable input during API calls
- **System messages**: Centered feedback for action results

### Common Patterns & Best Practices

#### UUID Serialization for LLMs

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

#### Event Sourcing for AI Conversations

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

#### Polylith Interface/Core Separation

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

#### Re-frame Effectful Pattern

**Flow**:
```
Component (pure) → Event (pure) → Effect (impure) → Success Event (pure) → Component re-renders
```

**Benefits**:
- Pure functions are testable
- Clear separation of concerns
- Time-travel debugging
- Easy to reason about

### Troubleshooting & Common Pitfalls

#### 1. Polylith Dependency Violations

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

#### 2. Re-frame Initialization Order

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

#### 3. UUID Serialization Problems

**Error**: LLM receives `{"item-id": {"$uuid": "..."}}` instead of string.

**Fix**: Use `stringify-uuids` before passing data to DSPy:

```clojure
(swap! st-memory assoc :pantry_context
  {:items (stringify-uuids pantry-items)})
```

#### 4. DSPy Action Schema Design

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

#### 5. Conversation History Loading

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

### Quick Reference

#### File Locations Checklist

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

#### Environment Variables

```bash
# LLM Provider (OpenRouter recommended)
OPENROUTER_API_KEY=your_key_here
LLM_PROVIDER=openai/gpt-4o-mini
OPENROUTER_API_BASE=https://openrouter.ai/api/v1

# Or direct OpenAI
OPENAI_API_KEY=your_key_here
LLM_PROVIDER=gpt-4o-mini
```

#### REPL Testing Commands

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

#### Creating New AI Components

Use the Babashka script to scaffold a new component:

```bash
bb scripts/create_component.bb my-ai-service
```

Then add AI-specific files:
- `core/config.clj` - DSPy initialization
- `core/schemas.clj` - Malli schemas for agent state
- `core/signatures.clj` - DSPy signature definitions
- `core/behavior_trees.clj` - Behavior tree definitions

### Summary

Building AI features with Grain + DSPy follows this pattern:

1. **Define DSPy signature** with clear prompts and schemas
2. **Create behavior tree** with custom actions (load context, persist results)
3. **Write command handler** that builds and runs the tree
4. **Export via interface.clj** (commands, initialization)
5. **Wire to web-api** by merging command registry
6. **Build frontend** with re-frame effects/events/subs
7. **Create UI components** with subscriptions

The combination of behavior trees (orchestration), DSPy (type-safe LLM calls), and event sourcing (audit trail) provides a robust foundation for AI features that are testable, composable, and production-ready.
