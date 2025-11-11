(ns ai.obney.workshop.pantry-service.interface.schemas
  "Schemas for pantry-service commands, events, queries, and results.

   Defines all data structures for pantry tracking, shopping lists,
   and household management using Malli schemas."
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

;; Events
(defschemas events
  {:household/created [:map
                       [:household-id :uuid]
                       [:household-name :string]
                       [:created-by :uuid]]

   :household/member-added [:map
                            [:household-id :uuid]
                            [:user-id :uuid]]

   :pantry-item/added [:map
                       [:item-id :uuid]
                       [:household-id :uuid]
                       [:name :string]
                       [:quantity :string]
                       [:category :string]
                       [:expires [:maybe :string]]]

   :pantry-item/updated [:map
                         [:item-id :uuid]
                         [:household-id :uuid]
                         [:quantity [:maybe :string]]
                         [:expires [:maybe :string]]]

   :pantry-item/removed [:map
                         [:item-id :uuid]
                         [:household-id :uuid]]

   :shopping-item/added [:map
                         [:item-id :uuid]
                         [:household-id :uuid]
                         [:name :string]
                         [:quantity :string]
                         [:category :string]
                         [:for-recipe [:maybe :string]]]

   :shopping-item/checked [:map
                           [:item-id :uuid]
                           [:household-id :uuid]
                           [:checked :boolean]]

   :shopping-item/removed [:map
                           [:item-id :uuid]
                           [:household-id :uuid]]

   :shopping-items/cleared [:map
                            [:household-id :uuid]
                            [:item-ids [:vector :uuid]]]

   :shopping-items/moved-to-pantry [:map
                                    [:household-id :uuid]
                                    [:item-ids [:vector :uuid]]]})

;; Commands
(defschemas commands
  {:pantry/create-household [:map
                             [:household-name :string]]

   :pantry/add-item [:map
                     [:name :string]
                     [:quantity :string]
                     [:category :string]
                     [:expires [:maybe :string]]]

   :pantry/update-item [:map
                        [:item-id :uuid]
                        [:quantity [:maybe :string]]
                        [:expires [:maybe :string]]]

   :pantry/remove-item [:map
                        [:item-id :uuid]]

   :shopping/add-item [:map
                       [:name :string]
                       [:quantity :string]
                       [:category :string]
                       [:for-recipe [:maybe :string]]]

   :shopping/toggle-item [:map
                          [:item-id :uuid]
                          [:checked :boolean]]

   :shopping/remove-item [:map
                          [:item-id :uuid]]

   :shopping/clear-completed [:map]

   :shopping/move-to-pantry [:map
                             [:item-ids [:vector :uuid]]]})

;; Command Results
(defschemas command-results
  {:pantry/create-household-result [:map
                                    [:household-id :uuid]]

   :pantry/add-item-result [:map
                            [:item-id :uuid]]

   :pantry/update-item-result [:map
                               [:success :boolean]]

   :pantry/remove-item-result [:map
                               [:success :boolean]]

   :shopping/add-item-result [:map
                              [:item-id :uuid]]

   :shopping/toggle-item-result [:map
                                 [:success :boolean]]

   :shopping/remove-item-result [:map
                                 [:success :boolean]]

   :shopping/clear-completed-result [:map
                                     [:removed-count :int]]

   :shopping/move-to-pantry-result [:map
                                    [:moved-count :int]]})

;; Queries
(defschemas queries
  {:pantry/get-items [:map
                      [:category [:maybe :string]]
                      [:search [:maybe :string]]]

   :pantry/get-expiring-soon [:map
                              [:days :int]]

   :shopping/get-list [:map]

   :pantry/get-household [:map]})

;; Query Results
(defschemas query-results
  {:pantry/get-items-result [:vector
                             [:map
                              [:id :uuid]
                              [:name :string]
                              [:quantity :string]
                              [:category :string]
                              [:expires [:maybe :string]]]]

   :pantry/get-expiring-soon-result [:vector
                                     [:map
                                      [:id :uuid]
                                      [:name :string]
                                      [:expires :string]
                                      [:days-until :int]]]

   :shopping/get-list-result [:vector
                              [:map
                               [:id :uuid]
                               [:name :string]
                               [:quantity :string]
                               [:category :string]
                               [:checked :boolean]
                               [:for-recipe [:maybe :string]]]]

   :pantry/get-household-result [:map
                                 [:household-id :uuid]
                                 [:household-name :string]
                                 [:members [:vector :uuid]]]})

;; Read Models
(defschemas read-models
  {:pantry/households [:map-of :uuid
                       [:map
                        [:household-name :string]
                        [:members [:set :uuid]]]]

   :pantry/items [:map-of :uuid
                  [:map
                   [:household-id :uuid]
                   [:name :string]
                   [:quantity :string]
                   [:category :string]
                   [:expires [:maybe :string]]]]

   :shopping/items [:map-of :uuid
                    [:map
                     [:household-id :uuid]
                     [:name :string]
                     [:quantity :string]
                     [:category :string]
                     [:checked :boolean]
                     [:for-recipe [:maybe :string]]]]})
