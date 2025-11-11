(ns ai.obney.workshop.user-service.interface.schemas
  "The schemas ns in a grain service component defines the schemas for commands, events, queries, etc.
   
   It uses the `defschemas` macro to register the schemas centrally for the rest of
   the system to use. 
   
   Schemas are validated in places such as the command-processor
   and event-store."
  (:require [ai.obney.grain.schema-util.interface :refer [defschemas]]))

(defschemas events
  {:user/signed-up [:map
                    [:user-id :uuid]
                    [:email-address :string]
                    [:password :string]]
   :user/logged-in [:map
                    [:user-id :uuid]
                    [:email-address :string]]

   :user/welcome-email-sent [:map
                             [:user-id :uuid]
                             [:email-address :string]]

   :user/verification-link-sent [:map
                                 [:email-address :string]
                                 [:jwt :string]]
   :user/email-verified [:map
                         [:user-id :uuid]
                         [:email-address :string]]

   :user/password-reset-link-sent [:map
                                   [:email-address :string]
                                   [:jwt :string]]

   :user/password-reset [:map
                         [:user-id :uuid]
                         [:password :string]
                         [:jwt :string]]

   :user/password-reset-notification-sent [:map
                                           [:user-id :uuid]]})

(defschemas commands
  {:user/sign-up [:map
                  [:email-address :string]
                  [:password :string]]

   :user/login [:map
                [:email-address :string]
                [:password :string]]
   
   :user/logout [:map]

   :user/send-welcome-email [:map
                             [:user-id :uuid]
                             [:email-address :string]]

   :user/send-verification-link [:map
                                 [:user-id :uuid]
                                 [:email-address :string]]

   :user/verify-email [:map
                       [:jwt :string]]

   :user/request-password-reset [:map
                                 [:email-address :string]]

   :user/reset-password [:map
                         [:password :string]
                         [:jwt :string]]

   :user/send-password-reset-notification [:map
                                           [:user-id :uuid]]
   
   :user/check-session [:map]})



(defschemas read-models
  {:user/users [:map-of :uuid
                [:map
                 [:user-id :uuid]
                 [:email-address]]]
   :user/user [:map
               [:user-id :uuid]
               [:email-address]]})