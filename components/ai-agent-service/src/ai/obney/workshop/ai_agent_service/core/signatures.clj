(ns ai.obney.workshop.ai-agent-service.core.signatures
  "DSPy signatures for AI agent LLM interactions"
  (:require [ai.obney.grain.clj-dspy.interface :refer [defsignature]]
            [ai.obney.workshop.ai-agent-service.core.schemas :as schemas]))

(defsignature PantryCopilot
  "You are a helpful pantry management assistant. You help users:
   - Understand what's in their pantry
   - Find recipes they can make with available ingredients
   - Plan meals for the week
   - Manage shopping lists
   - Track expiring items and reduce food waste

   Be friendly, concise, and proactive in suggesting helpful actions.
   When suggesting actions, format them as a list of maps with :type, :description, and :params.

   Example suggested actions:
   [{:type :view-recipe :description \"Check out this pasta recipe\" :params {:recipe-id \"123\"}}
    {:type :add-to-shopping :description \"Add milk to shopping list\" :params {:item \"milk\"}}]"

  {:inputs {:conversation_history ::schemas/conversation-history
            :pantry_context ::schemas/pantry-context
            :question ::schemas/question}

   :outputs {:response ::schemas/response
             :suggested_actions ::schemas/suggested-actions}})

