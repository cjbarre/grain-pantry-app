#!/usr/bin/env bb

(ns create-component
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [babashka.fs :as fs]))

(def templates
  {:deps-edn
   "{:paths [\"src\" \"resources\"]
 :deps {}
 :aliases {:test {:extra-paths [\"test\"]
                  :extra-deps {}}}}"

   :interface-schemas
   "(ns ai.obney.workshop.{{component-name}}.interface.schemas
  \"The schemas ns in a grain service component defines the schemas for commands, events, queries, etc.
   
   It uses the `defschemas` macro to register the schemas centrally for the rest of
   the system to use. 
   
   Schemas are validated in places such as the command-processor
   and event-store.\"
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas events
  {{{namespace}}/example-event
   [:map
    [:name :string]]})

(defschemas commands
  {{{namespace}}/example-command
   [:map
    [:name :string]]})

(defschemas read-models
  {{{namespace}}/example-read-model
   [:map
    [:example [:map
               [:name :string]]]]})

(defschemas queries
  {{{namespace}}/example-query
   [:map]})"

   :core-commands
   "(ns ai.obney.workshop.{{component-name}}.core.commands
  \"The core commands namespace in a grain service component implements
   the command handlers and defines the command registry. Command functions
   take a context that includes any necessary dependencies, to be injected
   in the base for the service. Usually a command-request-handler or another 
   type of adapter will call the command processor, which will access the command 
   registry for the entire application in the context. Commands either return a cognitect 
   anomaly or a map that optionally has a :command-result/events key containing a sequence of 
   valid events per the event-store event schema and optionally :command/result which is some 
   data that is meant to be returned to the caller, see command-request-handler for example.\"
  (:require [ai.obney.workshop.{{component-name}}.core.read-models :as read-models]
            [ai.obney.grain.event-store-v2.interface :refer [->event]]
            [cognitect.anomalies :as anom]))

(defn example-command
  \"Example command handler.\"
  [context]
  (let [name (get-in context [:command :name])]
    {:command-result/events
     [(->event {:type {{namespace}}/example-event
                :tags #{[:example (random-uuid)]}
                :body {:name name}})]}))

(def commands
  {{{namespace}}/example-command {:handler-fn #'example-command}})"

   :core-queries
   "(ns ai.obney.workshop.{{component-name}}.core.queries
  \"The core queries namespace in a grain service component implements
     the query handlers and defines the query registry. Query functions
     take a context that includes any necessary dependencies, to be injected
     in the base for the service. Usually a query-request-handler or another 
     type of adapter will call the query processor, which will access the query 
     registry for the entire application in the context. Queries either return a cognitect 
     anomaly or a map that optionally has a :query/result which is some 
     data that is meant to be returned to the caller, see query-request-handler for example.\"
  (:require [ai.obney.workshop.{{component-name}}.core.read-models :as read-models]
            [ai.obney.grain.event-store-v2.interface :as event-store]
            [cognitect.anomalies :as anom]))

(defn example-query
  [{:keys [event-store] :as context}]
  (let [events (event-store/read
                event-store
                {:types read-models/{{component-name-snake}}-event-types})
        result (read-models/apply-events events)]
    {:query/result result}))

(def queries
  {{{namespace}}/example-query {:handler-fn #'example-query}})"

   :core-read-models
   "(ns ai.obney.workshop.{{component-name}}.core.read-models
  \"The core read-models namespace in a grain app is where projections are created from events.
   Events are retrieved using the event-store and the read model is built through reducing usually.
   These tend to be used by the other components of the grain app, such as commands, queries, periodic tasks,
   and todo-processors.\"
  (:require [ai.obney.grain.event-store-v2.interface :as event-store]
            [com.brunobonacci.mulog :as u]))

(def {{component-name-snake}}-event-types
  #{{{namespace}}/example-event})

(defmulti apply-event
  \"Apply an event to the read model.\"
  (fn [_state event]
    (:event/type event)))

(defmethod apply-event {{namespace}}/example-event
  [state {:keys [name]}]
  (assoc state :example {:name name}))

(defmethod apply-event :default
  [state _event]
  ;; If the event is not recognized, return the state unchanged.
  state)

(defn apply-events
  \"Applies a sequence of events to the read model state.\"
  [events]
  (let [result (reduce
                (fn [state event]
                  (apply-event state event))
                {}
                events)]
    (when (seq result)
      result)))"

   :core-todo-processors
   "(ns ai.obney.workshop.{{component-name}}.core.todo-processors
  \"The core todo-processors namespace in a grain service is where todo-processor handler functions are defined.
   These functions receive a context and have a specific return signature. They can return a cognitect anomaly,
   a map with a `:result/events` key containing a sequence of valid events per the event-store event 
   schema, or an empty map. Sometimes the todo-processor will just call a command through the commant-processor.
   The wiring up of the context and the function occurs in the grain app base. The todo-processor subscribes to 
   one or more events via pubsub and only ever processes a single event at a time, which is included in the context.\"
  (:require [ai.obney.grain.command-processor.interface :as command-processor]
            [ai.obney.grain.time.interface :as time]))

(defn example-todo-processor
  \"Example todo processor that processes events.\"
  [{:keys [_event] :as context}]
  ;; Example: calling a command in response to an event
  (command-processor/process-command
   (assoc context
          :command
          {:command/id (random-uuid)
           :command/timestamp (time/now)
           :command/name {{namespace}}/example-command
           :name \"processed-event\"})))

(def todo-processors
  {{{namespace}}/example-todo
   {:handler-fn #'example-todo-processor
    :topics [{{namespace}}/example-event]}})"

   :core-periodic-tasks
   "(ns ai.obney.workshop.{{component-name}}.core.periodic-tasks
  \"The periodic tasks namespace in a grain service component is where
   periodic task functions are defined. These functions accept a context,
   which is wired up in the base for the grain app, and the time, provided 
   by the periodic-task component implementation.
   
   Periodic tasks are less rigid than commands and todo-processors and generally
   do not have a specific return value. So they use the various dependencies in the context
   in order to perform their work with discretion.\"
  (:require [com.brunobonacci.mulog :as u]))

(defn example-periodic-task
  [_context _time]
  (u/trace ::example-periodic-task []))"

   :interface
   "(ns ai.obney.workshop.{{component-name}}.interface
  (:require [ai.obney.workshop.{{component-name}}.core.commands :as commands]
            [ai.obney.workshop.{{component-name}}.core.periodic-tasks :as tasks]
            [ai.obney.workshop.{{component-name}}.core.queries :as queries]
            [ai.obney.workshop.{{component-name}}.core.read-models :as rm]
            [ai.obney.workshop.{{component-name}}.core.todo-processors :as tp]))

(def commands commands/commands)

(def periodic-tasks
  {:example-periodic-task {:handler-fn #'tasks/example-periodic-task
                           :schedule \"0 0 * * * ?\"
                           :description \"Example periodic task\"}})

(def queries queries/queries)

(def todo-processors tp/todo-processors)

;; Read Models
(defn apply-events
  [events]
  (rm/apply-events events))

(def {{component-name-snake}}-event-types rm/{{component-name-snake}}-event-types)"})

(defn kebab-case [s]
  (-> s
      (str/replace #"_" "-")
      (str/replace #"([a-z])([A-Z])" "$1-$2")
      str/lower-case))

(defn snake-case [s]
  (-> s
      (str/replace #"-" "_")
      (str/replace #"([a-z])([A-Z])" "$1_$2")
      str/lower-case))

(defn pascal-case [s]
  (->> (str/split s #"[-_]")
       (map str/capitalize)
       (str/join)))

(defn create-directory [path]
  (when-not (fs/exists? path)
    (fs/create-dirs path)
    (println "Created directory:" path)))

(defn substitute-template [template component-name]
  (let [kebab-name (kebab-case component-name)
        snake-name (snake-case component-name)
        pascal-name (pascal-case component-name)
        namespace (str ":" kebab-name)]
    (-> template
        (str/replace "{{component-name}}" kebab-name)
        (str/replace "{{component-name-snake}}" snake-name)
        (str/replace "{{component-name-pascal}}" pascal-name)
        (str/replace "{{namespace}}" namespace))))

(defn write-file [path content]
  (io/make-parents path)
  (spit path content)
  (println "Created file:" path))

(defn create-component [component-name]
  (let [kebab-name (kebab-case component-name)
        snake-name (snake-case component-name)
        base-path (str "components/" kebab-name)
        src-path (str base-path "/src/ai/obney/workshop/" snake-name)
        test-path (str base-path "/test/ai/obney/workshop/" snake-name)
        resources-path (str base-path "/resources/" kebab-name)]
    
    (println "Creating component:" kebab-name)
    
    ;; Create directories
    (create-directory base-path)
    (create-directory (str src-path "/interface"))
    (create-directory (str src-path "/core"))
    (create-directory test-path)
    (create-directory resources-path)
    
    ;; Create files
    (write-file (str base-path "/deps.edn") 
                (substitute-template (:deps-edn templates) component-name))
    
    (write-file (str src-path "/interface.clj")
                (substitute-template (:interface templates) component-name))

    (write-file (str src-path "/interface/schemas.clj")
                (substitute-template (:interface-schemas templates) component-name))
    
    (write-file (str src-path "/core/commands.clj")
                (substitute-template (:core-commands templates) component-name))
    
    (write-file (str src-path "/core/queries.clj")
                (substitute-template (:core-queries templates) component-name))
    
    (write-file (str src-path "/core/read_models.clj")
                (substitute-template (:core-read-models templates) component-name))
    
    (write-file (str src-path "/core/todo_processors.clj")
                (substitute-template (:core-todo-processors templates) component-name))
    
    (write-file (str src-path "/core/periodic_tasks.clj")
                (substitute-template (:core-periodic-tasks templates) component-name))
    
    (println "Component" kebab-name "created successfully!")
    (println "Next steps:")
    (println "1. Add the component to your project's deps.edn")
    (println "2. Update the schemas with your actual commands, events, and queries")
    (println "3. Implement your business logic in the core namespaces")
    (println "4. Wire up the component in your base application")))

(defn -main [& args]
  (if (= 1 (count args))
    (create-component (first args))
    (do
      (println "Usage: bb create-component.bb COMPONENT_NAME")
      (println "Example: bb create-component.bb user-service")
      (System/exit 1))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))