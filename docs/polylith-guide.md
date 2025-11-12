# Polylith Architecture Guide

This guide explains how Polylith architecture is used in this project and best practices for working with it.

## What is Polylith?

Polylith is a software architecture that enables you to build systems using a collection of reusable components. It provides:

- **Component isolation**: Clear boundaries between components
- **Reusability**: Share components across projects
- **Development experience**: Work with all components in a single REPL
- **Incremental testing**: Only test what changed
- **Monorepo benefits**: Without the typical monorepo complexity

## Core Concepts

### Components

Components are the building blocks of your system. Each component:

- Lives in `components/<component-name>/`
- Has a public API exposed through `interface.clj`
- Has implementation details in `core/` subdirectories
- Can depend on other components (via interfaces only)

**Directory Structure**:

```
components/my-service/
├── deps.edn                    # Component dependencies
├── resources/                  # Component resources (optional)
├── test/                       # Component tests (optional)
└── src/ai/obney/workshop/my_service/
    ├── interface.clj           # Public API
    ├── interface/
    │   └── schemas.clj         # Public schemas
    └── core/
        ├── commands.clj        # Implementation
        ├── queries.clj
        └── read_models.clj
```

### Bases

Bases are entry points to your system (applications). They:

- Live in `bases/<base-name>/`
- Wire together components
- Define system configuration
- Cannot be depended upon by components

**Example**: `bases/web-api/` is the HTTP API server that wires together all service components.

### Projects

Projects are deployable artifacts. They:

- Live in `projects/<project-name>/`
- Combine a base with components
- Define deployment configuration
- Produce artifacts (JARs, Docker images, etc.)

## The Interface/Core Pattern

This is the most important pattern in Polylith:

### Interface (Public API)

The `interface.clj` file defines what other components can use:

```clojure
(ns ai.obney.workshop.pantry-service.interface
  (:require [ai.obney.workshop.pantry-service.core.commands :as commands]
            [ai.obney.workshop.pantry-service.core.queries :as queries]
            [ai.obney.workshop.pantry-service.core.read-models :as rm]))

;; Export command/query registries
(def commands commands/commands)
(def queries queries/queries)

;; Export read model functions needed by other components
(def pantry-event-types rm/pantry-event-types)
(def apply-pantry-events rm/apply-pantry-events)
```

### Core (Implementation)

The `core/` directory contains all implementation details:

```clojure
(ns ai.obney.workshop.pantry-service.core.commands
  (:require [ai.obney.grain.event-store-v2.interface :as es]))

(defn add-item [context]
  ;; Implementation details
  )

(def commands
  {:pantry/add-item {:handler-fn #'add-item}})
```

## Dependency Rules

### Legal Dependencies

✅ **Components can depend on**:
- Other components (via `interface.clj` only)
- External libraries

✅ **Bases can depend on**:
- Components (via `interface.clj` only)
- External libraries

✅ **Projects include**:
- One base
- All required components

### Illegal Dependencies

❌ **Components CANNOT**:
- Import from another component's `core/` namespace
- Depend on bases
- Depend on projects

❌ **Bases CANNOT**:
- Import from component's `core/` namespace
- Depend on other bases

## Common Patterns

### Pattern 1: Exposing Functions

**Wrong**:

```clojure
;; In ai-agent-service/core/behavior-trees.clj
(ns ai.obney.workshop.ai-agent-service.core.behavior-trees
  (:require [ai.obney.workshop.pantry-service.core.read-models :as rm]))  ; ❌

(defn load-context []
  (rm/apply-pantry-events events))  ; Direct access to core
```

**Right**:

```clojure
;; In pantry-service/interface.clj
(ns ai.obney.workshop.pantry-service.interface
  (:require [ai.obney.workshop.pantry-service.core.read-models :as rm]))

(def apply-pantry-events rm/apply-pantry-events)  ; Expose through interface
(def pantry-event-types rm/pantry-event-types)

;; In ai-agent-service/core/behavior-trees.clj
(ns ai.obney.workshop.ai-agent-service.core.behavior-trees
  (:require [ai.obney.workshop.pantry-service.interface :as pantry]))  ; ✅

(defn load-context []
  (pantry/apply-pantry-events events))  ; Access via interface
```

### Pattern 2: Shared Schemas

When multiple components need the same schemas:

**Option A**: Create a foundation component

```
components/common-schemas/
└── src/ai/obney/workshop/common_schemas/
    └── interface.clj
```

**Option B**: Each component defines its own schemas

This is preferred when schemas are slightly different or coupling would be unnatural.

### Pattern 3: Component Initialization

For components that need initialization (like DSPy):

```clojure
;; In ai-agent-service/interface.clj
(ns ai.obney.workshop.ai-agent-service.interface
  (:require [ai.obney.workshop.ai-agent-service.core.config :as config]))

(def initialize-dspy! config/initialize-dspy!)
(def configured? config/configured?)

;; In web-api base
(ns ai.obney.workshop.web-api.core
  (:require [ai.obney.workshop.ai-agent-service.interface :as ai-agent]))

(defmethod ig/init-key ::ai-agent-init [_ _]
  (ai-agent/initialize-dspy!))
```

### Pattern 4: Foundation Components vs Service Components

**Foundation Components**: Provide infrastructure (crypto, email, file storage)
- Generic and reusable
- No business logic
- Can be used across multiple projects

**Service Components**: Implement business logic (pantry-service, recipe-service)
- Domain-specific
- Use foundation components
- Specific to this application

## Working with Grain + Polylith

Grain is an event-sourcing framework built on top of Polylith. It adds conventions:

### Grain Service Component Structure

```
components/my-service/
└── src/ai/obney/workshop/my_service/
    ├── interface.clj                    # Exports commands/queries/todo-processors
    ├── interface/
    │   └── schemas.clj                  # Grain schemas (defschemas macro)
    └── core/
        ├── commands.clj                 # Command handlers
        ├── queries.clj                  # Query handlers
        ├── read_models.clj              # Event projections
        └── todo_processors.clj          # Event subscribers
```

### Grain Schema Registration

Schemas are registered globally using the `defschemas` macro:

```clojure
(ns ai.obney.workshop.pantry-service.interface.schemas
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas commands
  {:pantry/add-item [:map
                     [:name :string]
                     [:quantity :string]]})

(defschemas events
  {:pantry-item/added [:map
                       [:item-id :uuid]
                       [:name :string]]})
```

These schemas are automatically discovered and used for validation.

## Polylith CLI

### Common Commands

**Check workspace integrity**:
```bash
clj -A:poly check
```

This verifies:
- No illegal dependencies
- All interfaces are properly defined
- Components are correctly structured

**Show workspace info**:
```bash
clj -A:poly info
```

Shows:
- All components, bases, and projects
- Dependency graph
- Which components changed since last stable point

**Run tests**:
```bash
clj -A:poly test
```

Only runs tests for components that changed.

**Create a component**:
```bash
# Using the project's Babashka script
bb scripts/create_component.bb my-component
```

### Understanding Check Errors

**"Illegal dependency on namespace X.core.Y"**:

You're trying to import a `core/` namespace from another component.

**Fix**: Export what you need through `interface.clj`.

**"Component X has circular dependency with Y"**:

Components depend on each other, creating a cycle.

**Fix**: Extract shared code into a third component, or reconsider your component boundaries.

## Best Practices

### 1. Keep Interfaces Minimal

Only expose what other components actually need.

**Don't**:
```clojure
;; Exposing everything
(def add-item commands/add-item)
(def update-item commands/update-item)
(def delete-item commands/delete-item)
(def validate-item commands/validate-item)
(def calculate-price commands/calculate-price)
```

**Do**:
```clojure
;; Expose registries
(def commands commands/commands)
(def queries queries/queries)

;; Only expose specific functions if other components need them
(def pantry-event-types rm/pantry-event-types)
```

### 2. Don't Leak Implementation Details

**Don't**:
```clojure
;; Exposing internal data structures
(def item-cache commands/cache)  ; Internal state
(def db-connection core/db)       ; Implementation detail
```

**Do**:
```clojure
;; Expose behavior, not data
(def get-cached-items cache/get-items)
```

### 3. Use Qualified Keywords

This prevents naming collisions across components:

```clojure
;; Good
(defn process-item [item]
  (assoc item ::processed-at (java.time.Instant/now)))

;; Avoid
(defn process-item [item]
  (assoc item :processed-at (java.time.Instant/now)))  ; Could clash with other components
```

### 4. Test at Component Boundaries

Write tests for your interface functions:

```clojure
(ns ai.obney.workshop.pantry-service.interface-test
  (:require [clojure.test :refer [deftest is]]
            [ai.obney.workshop.pantry-service.interface :as pantry]))

(deftest test-apply-pantry-events
  (let [events [{:event/type :pantry-item/added
                 :item-id (random-uuid)
                 :name "Milk"}]
        result (pantry/apply-pantry-events events)]
    (is (= 1 (count result)))))
```

### 5. Document Your Interfaces

Add docstrings to exposed functions:

```clojure
(defn apply-pantry-events
  "Applies a sequence of pantry events to rebuild current state.

   Takes a collection of events and returns a map of item-id -> item.
   Events should have :event/type matching pantry-event-types.

   Example:
     (apply-pantry-events [{:event/type :pantry-item/added ...}])
     => {#uuid \"...\" {:item-id #uuid \"...\" :name \"Milk\"}}"
  [events]
  (rm/apply-pantry-events events))
```

## Debugging Dependency Issues

### Step 1: Run Check

```bash
clj -A:poly check
```

This will show any illegal dependencies.

### Step 2: Examine the Error

```
Illegal dependency on ai.obney.workshop.pantry-service.core.read-models in component ai-agent-service
```

### Step 3: Find the Import

Search for the illegal namespace in your component:

```bash
grep -r "pantry-service.core.read-models" components/ai-agent-service/
```

### Step 4: Fix Via Interface

1. Add the needed function to `pantry-service/interface.clj`
2. Change the require to use `pantry-service.interface`
3. Run `clj -A:poly check` again

## Component Boundaries: When to Split

### Create a new component when:

1. **Independent business domain**: "User management" vs "Pantry management"
2. **Reusability**: Could be used in other projects
3. **Team ownership**: Different teams own different components
4. **Deployment independence**: Component might be deployed separately

### Keep in one component when:

1. **Tightly coupled**: Changes often happen together
2. **Same domain**: "Pantry items" and "Pantry categories"
3. **Small scope**: Component would be tiny if split
4. **No reuse plan**: Won't be used elsewhere

## Migration Guide: Adding Polylith to Existing Code

If you have existing code and want to convert it to Polylith:

1. **Identify components**: Group related namespaces
2. **Create component structure**: Use `bb scripts/create_component.bb`
3. **Move code**: Move implementation to `core/`, public API to `interface.clj`
4. **Update requires**: Change imports to use `interface` namespaces
5. **Run check**: Fix any dependency violations
6. **Test**: Ensure everything still works

## Resources

- [Polylith Documentation](https://polylith.gitbook.io/)
- [Polylith Architecture](https://polylith.gitbook.io/polylith/architecture)
- Run `clj -A:poly help` for CLI documentation

## Summary

Key takeaways for working with Polylith in this project:

1. ✅ Always import via `interface.clj`, never from `core/`
2. ✅ Keep interfaces minimal and well-documented
3. ✅ Run `clj -A:poly check` frequently
4. ✅ Think about component boundaries early
5. ✅ Use qualified keywords to avoid collisions
6. ✅ Test at the interface level
7. ❌ Don't expose implementation details
8. ❌ Don't create circular dependencies
9. ❌ Don't import from other component's `core/` namespaces
