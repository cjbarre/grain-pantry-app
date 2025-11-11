(ns components.auth.core
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/label" :as label]
            [components.context.interface :as context]
            [anomalies :as anom]
            [clojure.string :as str]
            [store.auth.events :as auth-events]))

;; Validation schemas
(def email-schema [:and :string [:re #"^[^\s@]+@[^\s@]+\.[^\s@]+$"]])
(def password-schema [:and :string [:min 8]])

(defn logout! [api-client navigate-fn]
  ;; Dispatch to si-frame store - now properly calls logout endpoint
  (rf/dispatch [::auth-events/logout api-client navigate-fn]))

;; Check current authentication status with the server
(defn check-auth-status! [api-client]
  (rf/dispatch [::auth-events/check-session api-client]))

;; Sign In Form Component
(defui sign-in-form [{:keys [on-switch]}]
  (let [[form-state set-form-state!] (use-state {:email ""
                                                   :password ""
                                                   :loading false
                                                   :error nil})
        ctx (context/use-context)
        api-client (:api/client ctx)
        navigate! (:router/navigate! ctx)]
    
    ($ card/Card {:class "w-full max-w-md"}
       ($ card/CardHeader
          ($ card/CardTitle "Sign In")
          ($ card/CardDescription "Enter your credentials to access your account"))
       
       ($ card/CardContent
          ($ :form {:on-submit (fn [e]
                                 (.preventDefault e)
                                 (set-form-state! (assoc form-state :loading true :error nil))
                                 (rf/dispatch [::auth-events/login
                                               (:email form-state)
                                               (:password form-state)
                                               api-client
                                               navigate!
                                               (fn [error]
                                                 (set-form-state! (assoc form-state
                                                                         :loading false
                                                                         :error (or (::anom/message error)
                                                                                    "Sign in failed"))))]))}

             ($ :div {:class "space-y-4"}
                ($ :div {:class "space-y-2"}
                   ($ label/Label {:html-for "email"} "Email")
                   ($ input/Input {:id "email"
                                   :type "email"
                                   :placeholder "you@example.com"
                                   :value (:email form-state)
                                   :on-change #(set-form-state! (assoc form-state :email (.. % -target -value)))
                                   :disabled (:loading form-state)
                                   :required true}))

                ($ :div {:class "space-y-2"}
                   ($ label/Label {:html-for "password"} "Password")
                   ($ input/Input {:id "password"
                                   :type "password"
                                   :value (:password form-state)
                                   :on-change #(set-form-state! (assoc form-state :password (.. % -target -value)))
                                   :disabled (:loading form-state)
                                   :required true}))

                (when (:error form-state)
                  ($ :div {:class "text-sm text-red-600"}
                     (:error form-state)))

                ($ button/Button {:type "submit"
                                  :class "w-full"
                                  :disabled (:loading form-state)}
                   (if (:loading form-state)
                     "Signing in..."
                     "Sign In")))))
       
       ($ card/CardFooter {:class "flex flex-col space-y-2"}
          ($ button/Button {:variant "link"
                            :on-click #(on-switch :forgot-password)
                            :class "text-sm"}
             "Forgot password?")
          ($ :div {:class "text-sm text-muted-foreground"}
             "Don't have an account? "
             ($ button/Button {:variant "link"
                               :on-click #(on-switch :sign-up)
                               :class "p-0 h-auto"}
                "Sign up"))))))

;; Sign Up Form Component
(defui sign-up-form [{:keys [on-switch]}]
  (let [[form-state set-form-state!] (use-state {:email ""
                                                   :password ""
                                                   :confirm-password ""
                                                   :loading false
                                                   :error nil})
        ctx (context/use-context)
        api-client (:api/client ctx)]
    
    ($ card/Card {:class "w-full max-w-md"}
       ($ card/CardHeader
          ($ card/CardTitle "Sign Up")
          ($ card/CardDescription "Create a new account"))
       
       ($ card/CardContent
          ($ :form {:on-submit (fn [e]
                                  (.preventDefault e)
                                  (cond
                                    (not= (:password form-state) (:confirm-password form-state))
                                    (set-form-state! (assoc form-state :error "Passwords don't match"))
                                    
                                    (< (count (:password form-state)) 8)
                                    (set-form-state! (assoc form-state :error "Password must be at least 8 characters"))
                                    
                                    :else
                                    (do
                                      (set-form-state! (assoc form-state :loading true :error nil))
                                      (rf/dispatch [::auth-events/sign-up
                                                    (:email form-state)
                                                    (:password form-state)
                                                    api-client
                                                    #(on-switch :sign-in)
                                                    (fn [error]
                                                      (set-form-state! (assoc form-state
                                                                              :loading false
                                                                              :error (or (::anom/message error)
                                                                                         "Sign up failed"))))]))))}

             ($ :div {:class "space-y-4"}
                ($ :div {:class "space-y-2"}
                   ($ label/Label {:html-for "email"} "Email")
                   ($ input/Input {:id "email"
                                   :type "email"
                                   :placeholder "you@example.com"
                                   :value (:email form-state)
                                   :on-change #(set-form-state! (assoc form-state :email (.. % -target -value)))
                                   :disabled (:loading form-state)
                                   :required true}))
                
                ($ :div {:class "space-y-2"}
                   ($ label/Label {:html-for "password"} "Password")
                   ($ input/Input {:id "password"
                                   :type "password"
                                   :placeholder "At least 8 characters"
                                   :value (:password form-state)
                                   :on-change #(set-form-state! (assoc form-state :password (.. % -target -value)))
                                   :disabled (:loading form-state)
                                   :required true}))
                
                ($ :div {:class "space-y-2"}
                   ($ label/Label {:html-for "confirm-password"} "Confirm Password")
                   ($ input/Input {:id "confirm-password"
                                   :type "password"
                                   :value (:confirm-password form-state)
                                   :on-change #(set-form-state! (assoc form-state :confirm-password (.. % -target -value)))
                                   :disabled (:loading form-state)
                                   :required true}))
                
                (when (:error form-state)
                  ($ :div {:class "text-sm text-red-600"}
                     (:error form-state)))
                
                ($ button/Button {:type "submit"
                                  :class "w-full"
                                  :disabled (:loading form-state)}
                   (if (:loading form-state)
                     "Creating account..."
                     "Sign Up")))))
       
       ($ card/CardFooter {:class "text-sm text-muted-foreground"}
          "Already have an account? "
          ($ button/Button {:variant "link"
                            :on-click #(on-switch :sign-in)
                            :class "p-0 h-auto"}
             "Sign in")))))

;; Forgot Password Form Component
(defui forgot-password-form [{:keys [on-switch]}]
  (let [[form-state set-form-state!] (use-state {:email ""
                                                   :loading false
                                                   :error nil
                                                   :success false})
        ctx (context/use-context)
        api-client (:api/client ctx)]
    
    ($ card/Card {:class "w-full max-w-md"}
       ($ card/CardHeader
          ($ card/CardTitle "Reset Password")
          ($ card/CardDescription "Enter your email to receive a password reset link"))
       
       ($ card/CardContent
          (if (:success form-state)
            ($ :div {:class "space-y-4"}
               ($ :p {:class "text-green-600"}
                  "Check your email for a password reset link.")
               ($ button/Button {:variant "outline"
                                 :on-click #(on-switch :sign-in)
                                 :class "w-full"}
                  "Back to Sign In"))
            
            ($ :form {:on-submit (fn [e]
                                    (.preventDefault e)
                                    (set-form-state! (assoc form-state :loading true :error nil))
                                    (rf/dispatch [::auth-events/request-password-reset
                                                  (:email form-state)
                                                  api-client
                                                  #(set-form-state! (assoc form-state
                                                                           :loading false
                                                                           :success true))
                                                  (fn [error]
                                                    (set-form-state! (assoc form-state
                                                                            :loading false
                                                                            :error (or (::anom/message error)
                                                                                       "Failed to send reset email"))))]))}

               ($ :div {:class "space-y-4"}
                  ($ :div {:class "space-y-2"}
                     ($ label/Label {:html-for "email"} "Email")
                     ($ input/Input {:id "email"
                                     :type "email"
                                     :placeholder "you@example.com"
                                     :value (:email form-state)
                                     :on-change #(set-form-state! (assoc form-state :email (.. % -target -value)))
                                     :disabled (:loading form-state)
                                     :required true}))
                  
                  (when (:error form-state)
                    ($ :div {:class "text-sm text-red-600"}
                       (:error form-state)))
                  
                  ($ button/Button {:type "submit"
                                    :class "w-full"
                                    :disabled (:loading form-state)}
                     (if (:loading form-state)
                       "Sending..."
                       "Send Reset Link"))))))
       
       ($ card/CardFooter
          ($ button/Button {:variant "link"
                            :on-click #(on-switch :sign-in)
                            :class "w-full"}
             "Back to Sign In")))))

;; Reset Password Confirmation Form Component
(defui reset-password-form [{:keys [on-switch jwt]}]
  (let [[form-state set-form-state!] (use-state {:password ""
                                                   :confirm-password ""
                                                   :loading false
                                                   :error nil
                                                   :success false})
        ctx (context/use-context)
        api-client (:api/client ctx)]
    
    ($ card/Card {:class "w-full max-w-md"}
       ($ card/CardHeader
          ($ card/CardTitle "Reset Your Password")
          ($ card/CardDescription "Enter your new password"))
       
       ($ card/CardContent
          (if (:success form-state)
            ($ :div {:class "space-y-4"}
               ($ :p {:class "text-green-600"}
                  "Your password has been reset successfully.")
               ($ button/Button {:on-click #(on-switch :sign-in)
                                 :class "w-full"}
                  "Sign In"))
            
            ($ :form {:on-submit (fn [e]
                                    (.preventDefault e)
                                    (cond
                                      (not= (:password form-state) (:confirm-password form-state))
                                      (set-form-state! (assoc form-state :error "Passwords don't match"))
                                      
                                      (< (count (:password form-state)) 8)
                                      (set-form-state! (assoc form-state :error "Password must be at least 8 characters"))
                                      
                                      :else
                                      (do
                                        (set-form-state! (assoc form-state :loading true :error nil))
                                        (rf/dispatch [::auth-events/reset-password
                                                      (:password form-state)
                                                      jwt
                                                      api-client
                                                      #(set-form-state! (assoc form-state
                                                                               :loading false
                                                                               :success true))
                                                      (fn [error]
                                                        (set-form-state! (assoc form-state
                                                                                :loading false
                                                                                :error (or (::anom/message error)
                                                                                           "Failed to reset password"))))]))))}

               ($ :div {:class "space-y-4"}
                  ($ :div {:class "space-y-2"}
                     ($ label/Label {:html-for "password"} "New Password")
                     ($ input/Input {:id "password"
                                     :type "password"
                                     :placeholder "At least 8 characters"
                                     :value (:password form-state)
                                     :on-change #(set-form-state! (assoc form-state :password (.. % -target -value)))
                                     :disabled (:loading form-state)
                                     :required true}))
                  
                  ($ :div {:class "space-y-2"}
                     ($ label/Label {:html-for "confirm-password"} "Confirm New Password")
                     ($ input/Input {:id "confirm-password"
                                     :type "password"
                                     :value (:confirm-password form-state)
                                     :on-change #(set-form-state! (assoc form-state :confirm-password (.. % -target -value)))
                                     :disabled (:loading form-state)
                                     :required true}))
                  
                  (when (:error form-state)
                    ($ :div {:class "text-sm text-red-600"}
                       (:error form-state)))
                  
                  ($ button/Button {:type "submit"
                                    :class "w-full"
                                    :disabled (:loading form-state)}
                     (if (:loading form-state)
                       "Resetting..."
                       "Reset Password")))))))))

;; Email Verification Handler Component
(defui verify-email-handler [{:keys [jwt on-switch]}]
  (let [[verify-state set-verify-state!] (use-state {:loading true
                                                       :error nil
                                                       :success false})
        ctx (context/use-context)
        api-client (:api/client ctx)]
    
    ;; Automatically verify on mount
    (use-effect
      (fn []
        (when (and jwt api-client)
          (rf/dispatch [::auth-events/verify-email
                        jwt
                        api-client
                        #(set-verify-state! {:loading false
                                             :error nil
                                             :success true})
                        (fn [error]
                          (set-verify-state! {:loading false
                                              :error (or (::anom/message error)
                                                         "Failed to verify email")
                                              :success false}))]))
        (fn []))
      [jwt api-client])
    
    ($ card/Card {:class "w-full max-w-md"}
       ($ card/CardHeader
          ($ card/CardTitle "Email Verification")
          ($ card/CardDescription 
             (cond
               (:loading verify-state) "Verifying your email..."
               (:success verify-state) "Email verified successfully!"
               :else "Email verification failed")))
       
       ($ card/CardContent
          (cond
            (:loading verify-state)
            ($ :div {:class "text-center py-4"}
               "Please wait...")
            
            (:success verify-state)
            ($ :div {:class "space-y-4"}
               ($ :p {:class "text-green-600"}
                  "Your email has been verified. You can now sign in to your account.")
               ($ button/Button {:on-click #(on-switch :sign-in)
                                 :class "w-full"}
                  "Sign In"))
            
            :else
            ($ :div {:class "space-y-4"}
               ($ :p {:class "text-red-600"}
                  (:error verify-state))
               ($ button/Button {:variant "outline"
                                 :on-click #(on-switch :sign-in)
                                 :class "w-full"}
                  "Back to Sign In")))))))

;; Main Auth Component with routing
(defui main [{:keys [current-match]}]
  (let [[active-form set-active-form!] (use-state :sign-in)
        path (-> current-match :path)
        query-params (-> current-match :query-params)
        jwt (or (:jwt query-params) (:token query-params))
        
        ;; Parse path to determine which form to show
        _ (use-effect
            (fn []
              (cond
                (str/ends-with? path "/sign-up") (set-active-form! :sign-up)
                (str/ends-with? path "/forgot-password") (set-active-form! :forgot-password)
                (str/ends-with? path "/reset-password") (set-active-form! :reset-password)
                (str/ends-with? path "/verify-email") (set-active-form! :verify-email)
                :else (set-active-form! :sign-in)))
            [path])]
    
    ($ :div {:class "min-h-screen flex items-center justify-center bg-background p-4"}
       (case active-form
         :sign-in ($ sign-in-form {:on-switch set-active-form!})
         :sign-up ($ sign-up-form {:on-switch set-active-form!})
         :forgot-password ($ forgot-password-form {:on-switch set-active-form!})
         :reset-password ($ reset-password-form {:on-switch set-active-form! :jwt jwt})
         :verify-email ($ verify-email-handler {:on-switch set-active-form! :jwt jwt})
         ($ sign-in-form {:on-switch set-active-form!})))))