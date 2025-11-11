(ns ai.obney.workshop.ai-agent-service.interface.schemas
  "Schemas for AI agent service commands, events, and results."
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

;; Events
(defschemas events
  {:ai/question-asked [:map
                       [:question :string]
                       [:household-id :uuid]
                       [:user-id :uuid]]

   :ai/response-generated [:map
                           [:response :string]
                           [:suggested-actions [:maybe [:vector :map]]]]})

;; Commands
(defschemas commands
  {:ai/ask [:map
            [:question :string]]})

;; Command Results
(defschemas command-results
  {:ai/ask-result [:map
                   [:response :string]
                   [:suggested-actions [:maybe [:vector [:map
                                                          [:type :keyword]
                                                          [:description :string]
                                                          [:params :map]]]]]]})
