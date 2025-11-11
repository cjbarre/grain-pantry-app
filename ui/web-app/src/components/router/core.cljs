(ns components.router.core
  (:require [reitit.frontend :as rf]
            [pushy.core :as pushy] 
            [uix.core :as uix :refer [defui $]]
            [re-frame.uix :refer [use-subscribe]]
            [components.auth.interface :as auth]
            [components.context.interface :as context]
            [store.auth.subs :as auth-subs] 
            [components.home.interface :as home]))

(def routes
  [["/" {:name :root :redirect "/home"}]
   ["/home" {:name :home :view home/main}]
   ["/auth"
    ["" {:name :auth :view auth/main}]
    ["/*path" {:name :auth-sub :view auth/main}]]])

(defonce router (rf/router routes))
(defonce match (atom nil))

(defonce history
  (pushy/pushy 
    #(reset! match %)
    #(rf/match-by-path router %)))

(defui router-outlet []
  (let [[current-match set-current-match!] (uix/use-state @match)
        auth-status (use-subscribe [::auth-subs/status])
        ctx (context/use-context)
        navigate! (:router/navigate! ctx)]
    
    ;; Watch the match atom and update component state when it changes
    (uix/use-effect
      (fn []
        (let [watch-key (gensym "router-watch")]
          (add-watch match watch-key
                    (fn [_ _ _ new-val]
                      (set-current-match! new-val)))
          ;; Cleanup function
          (fn []
            (remove-watch match watch-key))))
      [])
    
    (let [view (get-in current-match [:data :view])
          redirect (get-in current-match [:data :redirect])
          route-name (get-in current-match [:data :name])]
      (cond
        redirect (do (js/setTimeout #(set! js/window.location.pathname redirect) 0) nil)
        
        ;; Don't render anything while checking auth status for protected routes
        (and (#{:home} route-name) (= auth-status :loading))
        nil

        ;; Check authentication for protected routes
        (and (#{:home} route-name) (not= auth-status true))
        (do (js/setTimeout #(navigate! :auth) 0) nil)
        
        view ($ view {:current-match current-match})
        :else nil))))

(defn start-router! []
  (pushy/start! history))

(defn navigate!
  ([route-name]
   (navigate! route-name {}))
  ([route-name params]
   (let [route-match (rf/match-by-name router route-name params)
         path (:path route-match)]
     (when path
       (pushy/set-token! history path)))))