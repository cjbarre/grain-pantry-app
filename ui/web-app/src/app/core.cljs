(ns app.core
  (:require [uix.core :as uix :refer [defui $]]
            [uix.dom]
            [re-frame.core :as rf]
            [components.router.interface :as router]
            [components.context.interface :as context]
            [components.api.interface :as api]
            [components.auth.interface :as auth]

            [components.dev-banner.interface :as dev-banner]
            [config.core :as config]
            [store.core :as store]
            [store.auth.effects]
            [store.auth.events]
            [store.auth.subs]
            [store.ai.effects]
            [store.ai.events]
            [store.ai.subs]))

(defui app []
  ($ :<>
     ($ dev-banner/development-banner)
     ($ router/router)))

(defonce root
  (uix.dom/create-root (js/document.getElementById "root")))

(defn ^:dev/after-load render []
  ;; Create the API client and session manager
  (let [api-client (api/->RemoteAPIClient {:base-url (config/api-base-url)})
        app-context {:api/client api-client
                     :dev/show-banner true
                     :router/navigate! router/navigate!}]
    (uix.dom/render-root
     ($ uix/strict-mode
        ($ context/app-provider {:context app-context}
           ($ app)))
     root)))

(defn ^:export init []
  ;; Create API client and initialize store with it
  (let [api-client (api/->RemoteAPIClient {:base-url (config/api-base-url)})]
    (rf/dispatch-sync [::store/initialize api-client])
    (router/start-router!)
    ;; Check authentication status on app startup using refactored auth
    (auth/check-auth-status! api-client))
  (render)) 



(comment
  
  (require '[ai.obney.bryc.intake-service.interface.schemas :as s])



  ""
  )