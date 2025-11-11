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
