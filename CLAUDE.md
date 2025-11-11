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
