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
                           [:suggested-actions [:maybe [:vector :map]]]]

   ;; Recipe search events
   :ai/recipes-searched [:map
                         [:query :string]
                         [:results-count :int]
                         [:household-id :uuid]]

   :ai/recipe-suggested [:map
                         [:recipe-id :string]
                         [:recipe-title :string]
                         [:reasoning :string]
                         [:match-score :int]
                         [:household-id :uuid]]})

;; Commands
(defschemas commands
  {:ai/ask [:map
            [:question :string]]

   :ai/search-recipes [:map]})

;; Command Results
(defschemas command-results
  {:ai/ask-result [:map
                   [:response :string]
                   [:suggested-actions [:maybe [:vector [:map
                                                          [:type :keyword]
                                                          [:description :string]
                                                          [:params :map]]]]]]

   :ai/search-recipes-result [:map
                              [:recipes [:vector [:map
                                                  [:id :string]
                                                  [:title :string]
                                                  [:url [:maybe :string]]
                                                  [:description [:maybe :string]]
                                                  [:ai-reasoning [:maybe :string]]
                                                  [:match-score [:maybe :int]]]]]
                              [:reasoning [:maybe :string]]]})
