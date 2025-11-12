# Frontend Development Guide

This guide covers ClojureScript/React frontend development using UIx and Re-frame in this project.

## Technology Stack

- **ClojureScript**: Clojure compiled to JavaScript
- **shadow-cljs**: Build tool for ClojureScript
- **UIx**: React wrapper for ClojureScript (uses hooks)
- **Re-frame**: State management library (reactive data flow)
- **shadcn/ui**: UI component library (TypeScript components wrapped for ClojureScript)
- **Tailwind CSS v4**: Utility-first CSS framework

## Re-frame Architecture

Re-frame follows a unidirectional data flow pattern:

```
Component → Event → Effect → API Call → Success/Failure Event → DB Update → Subscription → Component Update
```

### Core Concepts

1. **App DB**: Single atom containing all application state
2. **Events**: Pure functions that describe what happened
3. **Effects**: Impure functions that perform side effects (API calls, etc.)
4. **Subscriptions**: Reactive queries into the app DB
5. **Components**: React components that subscribe to data and dispatch events

## File Structure

Frontend code is organized by feature:

```
ui/web-app/src/
├── components/           # UI components
│   ├── api/             # API client
│   ├── pantry/          # Pantry feature UI
│   ├── ai/              # AI sidebar UI
│   └── shadcn/          # shadcn UI wrappers
├── store/               # Re-frame state management
│   ├── pantry/
│   │   ├── events.cljs   # Event handlers
│   │   ├── effects.cljs  # Side effect handlers
│   │   └── subs.cljs     # Subscriptions
│   └── ai/
│       ├── events.cljs
│       ├── effects.cljs
│       └── subs.cljs
└── app/
    └── core.cljs        # Application entry point
```

## API Client

The API client is a protocol-based abstraction over HTTP:

```clojure
(ns components.api.core
  (:require [cljs-http.client :as http]
            [cljs.core.async :refer [<!]]
            [cognitect.transit :as transit]))

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

**Key Points**:
- Uses Transit for serialization (efficient and supports Clojure data types)
- Automatically adds IDs and timestamps to commands/queries
- Returns core.async channels for async operations

## Re-frame Effects

Effects are pure side-effect handlers. They never touch the app DB directly.

**Template**:

```clojure
(ns store.feature.effects
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]))

(rf/reg-fx
  ::my-effect
  (fn [{:keys [data api-client on-success on-failure]}]
    (go
      (let [response (<! (api/command api-client
                           {:command/name :my/command
                            :data data}))]
        (if (anomaly? response)
          (rf/dispatch (conj on-failure response))
          (rf/dispatch (conj on-success response)))))))
```

**Best Practices**:
- Effects are registered once at namespace load time
- Take a single map parameter for flexibility
- Always provide `on-success` and `on-failure` callbacks
- Use core.async `go` blocks for async operations
- Never modify app DB from effects - dispatch events instead

## Re-frame Events

Events orchestrate effects and update the app DB.

**Event Types**:

1. **`reg-event-db`**: Synchronous DB updates (no side effects)
2. **`reg-event-fx`**: Can trigger effects and update DB

**Examples**:

```clojure
(ns store.feature.events
  (:require [re-frame.core :as rf]
            [store.feature.effects :as fx]))

;; Event that triggers an effect
(rf/reg-event-fx
  ::fetch-items
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:feature :loading] true)
     ::fx/fetch-items {:api-client api-client
                       :on-success [::fetch-items-success]
                       :on-failure [::fetch-items-failure]}}))

;; Event that only updates DB (success handler)
(rf/reg-event-db
  ::fetch-items-success
  (fn [db [_ items]]
    (-> db
        (assoc-in [:feature :items] items)
        (assoc-in [:feature :loading] false))))

;; Event that only updates DB (failure handler)
(rf/reg-event-db
  ::fetch-items-failure
  (fn [db [_ error]]
    (-> db
        (assoc-in [:feature :error] error)
        (assoc-in [:feature :loading] false))))
```

**Best Practices**:
- Keep events pure and testable
- Use threading macros (`->`, `->>`) for clarity
- Set loading states before async operations
- Clear loading states in both success and failure handlers
- Use qualified keywords for event names (`::event-name`)

## Re-frame Subscriptions

Subscriptions are reactive queries into the app DB.

```clojure
(ns store.feature.subs
  (:require [re-frame.core :as rf]))

;; Simple subscription
(rf/reg-sub
  ::items
  (fn [db _]
    (get-in db [:feature :items] [])))

;; Subscription with default value
(rf/reg-sub
  ::loading
  (fn [db _]
    (get-in db [:feature :loading] false)))

;; Derived subscription (computes based on other subscriptions)
(rf/reg-sub
  ::filtered-items
  :<- [::items]
  :<- [::filter-text]
  (fn [[items filter-text] _]
    (if (empty? filter-text)
      items
      (filter #(clojure.string/includes? (:name %) filter-text) items))))
```

**Best Practices**:
- Keep subscriptions small and focused
- Provide sensible default values
- Use layer-2 subscriptions (`:<-`) for derived data
- Subscriptions are cached automatically by Re-frame

## UIx Components

UIx is a React wrapper that uses hooks for state management.

**Basic Component**:

```clojure
(ns components.feature.view
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [store.feature.subs :as subs]
            [store.feature.events :as events]
            [components.context :as context]))

(defui item-card
  "Component for displaying a single item."
  [{:keys [item on-click]}]
  ($ :div {:class "border p-4 rounded hover:bg-gray-50 cursor-pointer"
           :on-click on-click}
     ($ :h3 {:class "font-bold"} (:name item))
     ($ :p {:class "text-gray-600"} (:description item))))

(defui items-list
  "Component for displaying a list of items."
  []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)
        items (use-subscribe [::subs/items])
        loading (use-subscribe [::subs/loading])

        ;; Fetch items on mount
        _ (use-effect
            (fn []
              (rf/dispatch [::events/fetch-items api-client])
              ;; Cleanup function (optional)
              (fn []))
            [api-client])]  ; Dependencies

    ($ :div {:class "space-y-4"}
       (if loading
         ($ :p "Loading...")
         (for [item items]
           ($ item-card {:key (:id item)
                        :item item
                        :on-click #(rf/dispatch [::events/select-item (:id item)])}))))))
```

**Key Patterns**:

1. **Context**: Use React context for global values (API client, etc.)
2. **Subscriptions**: Use `use-subscribe` to get reactive data
3. **Effects**: Use `use-effect` for lifecycle operations
4. **Local State**: Use `use-state` for component-local state
5. **Hiccup Syntax**: Use `$` to create elements (equivalent to JSX)

### UIx Hooks

**`use-state`**: Local component state

```clojure
(defui counter []
  (let [[count set-count] (use-state 0)]
    ($ :div
       ($ :p "Count: " count)
       ($ :button {:on-click #(set-count (inc count))} "Increment"))))
```

**`use-effect`**: Side effects and lifecycle

```clojure
(defui my-component []
  (let [_ (use-effect
            (fn []
              (println "Component mounted")
              ;; Cleanup function
              (fn []
                (println "Component unmounted")))
            [])]  ; Empty deps = run once on mount
    ($ :div "Hello")))
```

**`use-subscribe`**: Re-frame subscription hook

```clojure
(defui my-component []
  (let [items (use-subscribe [::subs/items])]
    ($ :div
       (for [item items]
         ($ :div {:key (:id item)} (:name item))))))
```

**`use-ref`**: Mutable reference (like React's useRef)

```clojure
(defui scroll-to-bottom []
  (let [messages (use-subscribe [::subs/messages])
        bottom-ref (use-ref nil)

        _ (use-effect
            (fn []
              (when @bottom-ref
                (.scrollIntoView @bottom-ref #js {:behavior "smooth"})))
            [(count messages)])]

    ($ :div
       (for [msg messages]
         ($ :div {:key (:id msg)} (:text msg)))
       ($ :div {:ref bottom-ref}))))
```

## Working with shadcn Components

shadcn components are TypeScript React components that need to be wrapped for ClojureScript.

**Example Wrapper**:

```clojure
(ns components.shadcn.button
  (:require ["@/components/ui/button" :refer [Button]]))

(defn button [props & children]
  [:> Button props children])
```

**Usage in UIx**:

```clojure
($ button/Button
   {:variant "outline"
    :size "sm"
    :on-click #(rf/dispatch [::events/do-something])}
   "Click Me")
```

## Common Patterns

### Optimistic Updates

Update UI immediately, then sync with server:

```clojure
(rf/reg-event-fx
  ::add-item
  (fn [{:keys [db]} [_ item api-client]]
    {:db (update-in db [:feature :items] conj item)  ; Optimistic update
     ::fx/add-item {:item item
                    :api-client api-client
                    :on-success [::add-item-success]
                    :on-failure [::add-item-failure item]}}))  ; Pass item for rollback

(rf/reg-event-db
  ::add-item-failure
  (fn [db [_ item error]]
    ;; Rollback optimistic update
    (update-in db [:feature :items] #(remove (fn [i] (= i item)) %))))
```

### Loading States

Track loading for different operations:

```clojure
;; In DB
{:feature {:loading {:fetch false
                     :create false
                     :delete false}}}

;; Set loading
(rf/reg-event-db
  ::set-loading
  (fn [db [_ operation loading?]]
    (assoc-in db [:feature :loading operation] loading?)))

;; Check loading
(rf/reg-sub
  ::loading?
  (fn [db [_ operation]]
    (get-in db [:feature :loading operation] false)))
```

### Form Handling

```clojure
(defui item-form []
  (let [[name set-name] (use-state "")
        [quantity set-quantity] (use-state "")
        loading (use-subscribe [::subs/loading? :create])
        api-client (context/use-api-client)

        handle-submit (fn [e]
                       (.preventDefault e)
                       (rf/dispatch [::events/create-item
                                    {:name name :quantity quantity}
                                    api-client
                                    #(do (set-name "") (set-quantity ""))]))]

    ($ :form {:on-submit handle-submit}
       ($ input/Input {:value name
                      :on-change #(set-name (.. % -target -value))
                      :placeholder "Name"
                      :disabled loading})
       ($ input/Input {:value quantity
                      :on-change #(set-quantity (.. % -target -value))
                      :placeholder "Quantity"
                      :disabled loading})
       ($ button/Button {:type "submit" :disabled loading}
          (if loading "Creating..." "Create")))))
```

### Error Handling

```clojure
;; Display errors in UI
(rf/reg-sub
  ::error
  (fn [db _]
    (get-in db [:feature :error])))

(defui error-banner []
  (let [error (use-subscribe [::subs/error])]
    (when error
      ($ :div {:class "bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded"}
         ($ :p (:message error))
         ($ button/Button {:on-click #(rf/dispatch [::events/clear-error])}
            "Dismiss")))))
```

## Development Workflow

### Starting the Dev Server

```bash
cd ui/web-app
npm run dev
```

This starts:
- shadow-cljs dev server on http://localhost:8080
- Tailwind CSS watch mode
- TypeScript watch mode for shadcn components

### Hot Reload

shadow-cljs supports hot reload out of the box:
- Edit ClojureScript files → auto-reload
- Edit CSS → auto-reload
- Edit TypeScript components → rebuild and reload

### REPL Connection

Connect to the ClojureScript REPL:

```bash
# In a separate terminal
npx shadow-cljs cljs-repl app
```

Or connect from your editor (Emacs/VSCode/IntelliJ).

### Building for Production

```bash
npm run build        # Generic build
npm run build:dev    # Dev API
npm run build:staging
npm run build:prod
```

## Debugging Tips

### Re-frame Tracing

Enable re-frame-10x for debugging:

```clojure
;; In shadow-cljs.edn
{:dev {:closure-defines {day8.re-frame.tracing.trace-enabled? true}}}
```

Then open the re-frame panel (Ctrl+H in browser).

### Logging

Use `tap>` for logging:

```clojure
(tap> {:event ::my-event :data data})
```

View logs in browser console or connect a tap listener.

### Common Issues

1. **Subscription not updating**: Make sure you're using `use-subscribe`, not `@(rf/subscribe ...)`
2. **Effect not triggering**: Check that effect is registered and event is dispatching it correctly
3. **Component not re-rendering**: Verify subscription returns different value (use `=` comparison)

## Best Practices Summary

1. **Re-frame Events**: Keep them pure, use threading macros
2. **Re-frame Effects**: Never modify DB, always dispatch events
3. **Re-frame Subscriptions**: Keep them small, provide defaults
4. **UIx Components**: Use hooks, keep components focused
5. **State Management**: Prefer app DB over local state for shared data
6. **Error Handling**: Always handle both success and failure cases
7. **Loading States**: Show feedback during async operations
8. **Optimistic Updates**: Improve perceived performance
9. **Code Organization**: Group by feature, not by type
10. **Testing**: Write tests for events and subscriptions (pure functions)
