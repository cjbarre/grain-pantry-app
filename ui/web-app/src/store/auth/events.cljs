(ns store.auth.events
  "Auth-related re-frame events"
  (:require [re-frame.core :as rf]
            [store.router.effects :as router-fx]
            [store.auth.effects :as auth-fx]))

(rf/reg-event-db
  ::set-status
  (fn [db [_ status]]
    (assoc-in db [:auth :status] status)))

(rf/reg-event-db
  ::set-user
  (fn [db [_ user]]
    (assoc-in db [:auth :user] user)))

;; Check session with server
(rf/reg-event-fx
  ::check-session
  (fn [{:keys [db]} [_ api-client]]
    {:db (assoc-in db [:auth :status] :loading)
     ::auth-fx/check-session {:api-client api-client
                              :on-success [::check-session-success]
                              :on-failure [::check-session-failure]}}))

(rf/reg-event-db
  ::check-session-success
  (fn [db [_ response]]
    (let [user {:email (:email response)}]
      (-> db
          (assoc-in [:auth :status] true)
          (assoc-in [:auth :user] user)))))

(rf/reg-event-db
  ::check-session-failure
  (fn [db [_ _error]]
    (-> db
        (assoc-in [:auth :status] false)
        (assoc-in [:auth :user] nil))))

;; Login flow
(rf/reg-event-fx
  ::login
  (fn [_ctx [_ email password api-client on-success on-failure]]
    {::auth-fx/login {:email email
                      :password password
                      :api-client api-client
                      :on-success [::login-success on-success]
                      :on-failure [::login-failure on-failure]}}))

(rf/reg-event-fx
  ::login-success
  (fn [{:keys [db]} [_ on-success user]]
    (cond-> {:db (-> db
                     (assoc-in [:auth :status] true)
                     (assoc-in [:auth :user] user))}
      on-success (assoc ::router-fx/navigate {:route-name :pantry
                                               :navigate-fn on-success}))))

(rf/reg-event-db
  ::login-failure
  (fn [db [_ on-failure error]]
    ;; on-failure is a callback function from the component
    ;; Call it with the error
    (when on-failure
      (on-failure error))
    db))

;; Logout flow - calls API then clears state
(rf/reg-event-fx
  ::logout
  (fn [{:keys [db]} [_ api-client navigate-fn]]
    {:db db
     ::auth-fx/logout {:api-client api-client
                       :on-success [::logout-success navigate-fn]
                       :on-failure [::logout-failure navigate-fn]}}))

(rf/reg-event-fx
  ::logout-success
  (fn [{:keys [db]} [_ navigate-fn]]
    (cond-> {:db (-> db
                     (assoc-in [:auth :status] false)
                     (assoc-in [:auth :user] nil))}
      navigate-fn (assoc ::router-fx/navigate {:route-name :auth
                                                :navigate-fn navigate-fn}))))

(rf/reg-event-fx
  ::logout-failure
  (fn [{:keys [db]} [_ navigate-fn _error]]
    ;; Even if API call fails, clear local state and navigate
    (cond-> {:db (-> db
                     (assoc-in [:auth :status] false)
                     (assoc-in [:auth :user] nil))}
      navigate-fn (assoc ::router-fx/navigate {:route-name :auth
                                                :navigate-fn navigate-fn}))))

;; Sign up flow
(rf/reg-event-fx
  ::sign-up
  (fn [_ctx [_ email password api-client on-success on-failure]]
    {::auth-fx/sign-up {:email email
                        :password password
                        :api-client api-client
                        :on-success [::sign-up-success on-success]
                        :on-failure [::sign-up-failure on-failure]}}))

(rf/reg-event-db
  ::sign-up-success
  (fn [db [_ on-success]]
    (when on-success
      (on-success))
    db))

(rf/reg-event-db
  ::sign-up-failure
  (fn [db [_ on-failure error]]
    (when on-failure
      (on-failure error))
    db))

;; Password reset request flow
(rf/reg-event-fx
  ::request-password-reset
  (fn [_ctx [_ email api-client on-success on-failure]]
    {::auth-fx/request-password-reset {:email email
                                        :api-client api-client
                                        :on-success [::request-password-reset-success on-success]
                                        :on-failure [::request-password-reset-failure on-failure]}}))

(rf/reg-event-db
  ::request-password-reset-success
  (fn [db [_ on-success]]
    (when on-success
      (on-success))
    db))

(rf/reg-event-db
  ::request-password-reset-failure
  (fn [db [_ on-failure error]]
    (when on-failure
      (on-failure error))
    db))

;; Reset password flow
(rf/reg-event-fx
  ::reset-password
  (fn [_ctx [_ password jwt api-client on-success on-failure]]
    {::auth-fx/reset-password {:password password
                               :jwt jwt
                               :api-client api-client
                               :on-success [::reset-password-success on-success]
                               :on-failure [::reset-password-failure on-failure]}}))

(rf/reg-event-db
  ::reset-password-success
  (fn [db [_ on-success]]
    (when on-success
      (on-success))
    db))

(rf/reg-event-db
  ::reset-password-failure
  (fn [db [_ on-failure error]]
    (when on-failure
      (on-failure error))
    db))

;; Verify email flow
(rf/reg-event-fx
  ::verify-email
  (fn [_ctx [_ jwt api-client on-success on-failure]]
    {::auth-fx/verify-email {:jwt jwt
                             :api-client api-client
                             :on-success [::verify-email-success on-success]
                             :on-failure [::verify-email-failure on-failure]}}))

(rf/reg-event-db
  ::verify-email-success
  (fn [db [_ on-success]]
    (when on-success
      (on-success))
    db))

(rf/reg-event-db
  ::verify-email-failure
  (fn [db [_ on-failure error]]
    (when on-failure
      (on-failure error))
    db))
