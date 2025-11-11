(ns store.auth.effects
  "Re-frame effects for auth API calls"
  (:require [re-frame.core :as rf]
            [cljs.core.async :refer [go <!]]
            [components.api.interface :as api]
            [anomalies :refer [anomaly?]]))

(rf/reg-fx
  ::check-session
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/check-session}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success response))))))))

(rf/reg-fx
  ::login
  (fn [{:keys [email password api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/login
                                                     :email-address email
                                                     :password password}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch (conj on-success {:email email}))))))))

(rf/reg-fx
  ::logout
  (fn [{:keys [api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/logout}))]
          (if (anomaly? response)
            (when on-failure
              (rf/dispatch (conj on-failure response)))
            (when on-success
              (rf/dispatch on-success))))))))

(rf/reg-fx
  ::sign-up
  (fn [{:keys [email password api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/sign-up
                                                     :email-address email
                                                     :password password}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::request-password-reset
  (fn [{:keys [email api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/request-password-reset
                                                     :email-address email}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::reset-password
  (fn [{:keys [password jwt api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/reset-password
                                                     :password password
                                                     :jwt jwt}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))

(rf/reg-fx
  ::verify-email
  (fn [{:keys [jwt api-client on-success on-failure]}]
    (when api-client
      (go
        (let [response (<! (api/command api-client {:command/name :user/verify-email
                                                     :jwt jwt}))]
          (if (anomaly? response)
            (rf/dispatch (conj on-failure response))
            (rf/dispatch on-success)))))))
