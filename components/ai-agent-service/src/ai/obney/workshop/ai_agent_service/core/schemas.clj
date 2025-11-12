(ns ai.obney.workshop.ai-agent-service.core.schemas
  "Malli schemas for AI agent events and state"
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

;;
;; Agent State Schemas (for behavior tree memory)
;;

(defschemas agent-schemas
  {::conversation-history
   [:vector {:desc "Chat message history"}
    [:map
     [:role :string]
     [:content :string]]]

   ::pantry-context
   [:map {:desc "Current pantry state for AI context"}
    [:current-date :string]
    [:items [:vector :map]]
    [:expiring-soon [:vector :map]]
    [:categories [:vector :string]]
    [:shopping-list [:vector :map]]]

   ::question
   [:string {:desc "User's current question"}]

   ::response
   [:string {:desc "AI assistant's response"}]

   ::suggested-actions
   [:maybe [:vector {:desc "Actions AI suggests user take"}
            [:map
             [:type :keyword]
             [:description :string]
             [:params :map]]]]

   ;; Recipe search schemas
   ::web-search-results
   [:map {:desc "Raw web search results from Brave Search API"}
    [:web [:maybe [:map
                   [:results [:vector [:map
                                       [:title :string]
                                       [:description [:maybe :string]]
                                       [:url :string]]]]]]]
    [:query [:map
             [:original :string]]]]

   ::preference-signals
   [:map {:desc "User recipe preference signals learned from interactions"}
    [:viewed-recipes [:vector :string]]
    [:cooked-recipes [:vector :string]]
    [:dismissed-recipes [:vector :string]]]

   ::structured-recipe
   [:map {:desc "Parsed recipe data"}
    [:id :string]
    [:title :string]
    [:url [:maybe :string]]
    [:description [:maybe :string]]
    [:ingredients [:maybe [:vector :string]]]
    [:instructions [:maybe [:vector :string]]]
    [:time [:maybe :string]]
    [:difficulty [:maybe :string]]]

   ::structured-recipes
   [:vector {:desc "List of parsed recipes"}
    ::structured-recipe]

   ::recipe-reasoning
   [:string {:desc "AI's reasoning for why a recipe is suggested"}]

   ::recipe-reasoning-map
   [:map-of {:desc "Map of recipe IDs to reasoning"}
    :string ::recipe-reasoning]})
