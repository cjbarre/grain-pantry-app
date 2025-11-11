(ns components.api.core
  (:require [cljs-http.client :as http]
            [cljs.core.async :as async :refer [go <!]]
            [anomalies :as anom]))

;; API Client Protocol
(defprotocol APIClient
  "Protocol for backend API communication"
  (command [this cmd] "Send a command to the backend. Args: command map. Returns: core.async channel with response")
  (query [this qry] "Send a query to the backend. Args: query map. Returns: core.async channel with response"))

;; Helper function to process HTTP responses
(defn- process-response
  "Process HTTP response and convert non-2XX responses to anomalies"
  [response-chan]
  (go
    (let [response (<! response-chan)
          status (:status response)]
      (if (and status (>= status 200) (< status 300))
        ;; 2XX response - return as-is
        (:body response)
        ;; Non-2XX or error - create anomaly
        (let [body (:body response)
              message (or (:message body)
                         (str "Request failed with status " status))
              category (cond
                        (= status 400) ::anom/incorrect
                        (= status 401) ::anom/forbidden
                        (= status 403) ::anom/forbidden
                        (= status 404) ::anom/not-found
                        (= status 409) ::anom/conflict
                        (= status 429) ::anom/busy
                        (= status 500) ::anom/fault
                        (= status 503) ::anom/unavailable
                        :else ::anom/fault)]
          {::anom/category category
           ::anom/message message
           :status status
           :original-response response})))))

;; Remote implementation for production
(deftype RemoteAPIClient [config]
  APIClient
  (command [_ command]
    (let [url (str (:base-url config) "/command")
          command-with-meta (merge command
                                  {:command/timestamp (js/Date.)
                                   :command/id (str (random-uuid))})]
      (process-response
        (http/post url
                   {:transit-params {:command command-with-meta}
                    :headers (merge {"Content-Type" "application/transit+json"}
                                   (:headers config {}))}))))
  
  (query [_ query]
    (let [url (str (:base-url config) "/query")
          query-with-meta (merge query
                                {:query/timestamp (js/Date.)
                                 :query/id (str (random-uuid))})]
      (process-response
        (http/post url
                   {:transit-params {:query query-with-meta}
                    :headers (merge {"Content-Type" "application/transit+json"}
                                   (:headers config {}))})))))