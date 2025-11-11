(ns ai.obney.workshop.web-api.core
  (:require [ai.obney.grain.command-request-handler.interface :as crh]
            [ai.obney.grain.query-request-handler.interface :as qrh]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.event-store-postgres-v2.interface]
            [ai.obney.grain.webserver.interface :as ws]
            [ai.obney.grain.pubsub.interface :as ps]
            [ai.obney.grain.todo-processor.interface :as tp]
            [ai.obney.grain.mulog-aws-cloudwatch-emf-publisher.interface :as cloudwatch-emf]

            [ai.obney.workshop.jwt.interface :as jwt]
            [ai.obney.workshop.email-ses.interface :as email-ses]
            [ai.obney.workshop.crypto-kms.interface :as crypto-kms]

            [ai.obney.workshop.user-service.interface :as user-service]
            [ai.obney.workshop.user-service.interface.schemas]

            [clojure.set :as set]
            [com.brunobonacci.mulog :as u]
            [integrant.core :as ig]

            [io.pedestal.http :as http]
            [io.pedestal.http.ring-middlewares :as middlewares]
            [io.pedestal.interceptor :as interceptor]

            [config.core :refer [env]]
            [clojure.walk :as walk]

            [libpython-clj2.require :refer [require-python]]))

(require-python '[dspy :as dspy])

;; --------------------- ;;
;; Service Configuration ;;
;; --------------------- ;;

;;
;; This will be deleted later, just for testing ;;
;;

(def system
  {::logger {}

   ::event-store {:logger (ig/ref ::logger)
                  :event-pubsub (ig/ref ::event-pubsub)
                  :conn {:type :in-memory
                         ;; change to :postgres to try Postgres
                         ;; uncomment below to try Postgres
                         #_#_:server-name "localhost"
                         #_#_:port-number "5432"
                         #_#_:username "postgres"
                         #_#_:password "password"
                         #_#_:database-name "obneyai"}}

   ::event-pubsub {:type :core-async
                   :topic-fn :event/type}

   ::todo-processors {:event-pubsub (ig/ref ::event-pubsub)
                      :context (ig/ref ::context)}

   ::context {:event-store (ig/ref ::event-store)
              :command-registry (merge user-service/commands)
              :query-registry (merge user-service/queries)
              :event-pubsub (ig/ref ::event-pubsub)
              :email-client (email-ses/->SES (cond-> {}
                                               (:localstack/enabled env) (assoc :localstack/enabled true)
                                               (and (not (:localstack/enabled env))
                                                    (:aws/region env)) (assoc :aws/region (:aws/region env))))
              :email-from (:email-from env "noreply@demo.net")
              :jwt-secret (:jwt-secret env)
              :app-base (:app-base env)
              :crypto-provider (crypto-kms/->KmsCryptoProvider
                                {:kms-key-id (:kms-key-id env)
                                 :localstack/enabled (:localstack/enabled env)
                                 :localstack/endpoint (:localstack/endpoint env)
                                 :aws/region (:aws/region env)})}

   ::routes {:context (ig/ref ::context)}

   ::webserver {::http/routes (ig/ref ::routes)
                ::http/port (:webserver/http-port env)
                ::http/join? false
                ::http/allowed-origins {:allowed-origins (constantly true)
                                        :creds true}}

   ::dspy {}})

;; -------------- ;;
;; Integrant Keys ;;
;; -------------- ;;

(defn scrub-sensitive
  [data]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (contains? x :password))
       (assoc x :password :truncated)
       x))
   data))

(defn log-transform
  [logs]
  (map scrub-sensitive logs))

(defmethod ig/init-key ::logger [_ _]
  (let [console-pub-stop-fn
        (u/start-publisher! {:type :console-json
                             :pretty? false
                             :transform log-transform})

        cloudwatch-emf-pub-stop-fn
        (u/start-publisher!
         {:type :custom
          :fqn-function #'cloudwatch-emf/cloudwatch-emf-publisher
          :transform log-transform})]
    (fn []
      (console-pub-stop-fn)
      (cloudwatch-emf-pub-stop-fn))))

(defmethod ig/halt-key! ::logger [_ stop-fn]
  (stop-fn))

(defmethod ig/init-key ::dspy [_ _]
  (let [lm (dspy/LM "openai/anthropic/claude-sonnet-4.5"
                    :api_key (:openrouter-api-key env)
                    :cache false
                    :api_base "https://openrouter.ai/api/v1"
                    :max_tokens 8000)]

    (dspy/configure :lm lm)))

(defmethod ig/init-key ::event-store [_ config]
  (es/start config))

(defmethod ig/halt-key! ::event-store [_ event-store]
  (es/stop event-store))

(defmethod ig/init-key ::event-pubsub [_ config]
  (ps/start config))

(defmethod ig/halt-key! ::event-pubsub [_ event-pubsub]
  (ps/stop event-pubsub))

(defmethod ig/init-key ::todo-processors [_ {:keys [event-pubsub context]}]
  (mapv
   #(tp/start {:event-pubsub event-pubsub
               :context context
               :handler-fn (:handler-fn %)
               :topics (:topics %)})
   (concat
    (vals user-service/todo-processors))))

(defmethod ig/halt-key! ::todo-processors [_ todo-processors]
  (doseq [tp todo-processors]
    (tp/stop tp)))

(defmethod ig/init-key ::context [_ context]
  context)

(defmethod ig/init-key ::routes [_ {:keys [context]}]
  (set/union
   (crh/routes context)
   (qrh/routes context)
   #{["/healthcheck" :get [(fn [_] {:status 200 :body "OK"})] :route-name ::healthcheck]}))

(def set-auth-cookie
  (interceptor/interceptor
   {:name ::set-auth-cookie
    :leave
    (fn [context]
      (let [{:grain/keys [command-result command]
             :keys [response]} context
            status (get-in response [:status])]

        (cond
          ;; Set auth-token cookie on login
          (and (= status 200)
               (= (:command/name command) :user/login))
          (assoc-in context
                    [:response :cookies "auth-token"]
                    {:value (:jwt command-result)
                     :http-only true
                     :secure false   ;; in prod, usually true
                     :same-site :lax
                     :path "/"})

          ;; Clear auth-token cookie on logout
          (and (= status 200)
               (= (:command/name command) :user/logout))
          (assoc-in context
                    [:response :cookies "auth-token"]
                    {:value ""
                     :http-only true
                     :secure false   ;; in prod, usually true
                     :same-site :lax
                     :path "/"})

          :else context)))}))

(def extract-auth-cookie
  (interceptor/interceptor
   {:name ::extract-auth-cookie
    :enter
    (fn [context]
      (let [token (get-in context [:request :cookies "auth-token" :value])
            payload (try (jwt/unsign {:token token :secret (:jwt-secret env)})
                         (catch Exception _))]
        (cond-> context
          payload (assoc-in [:grain/additional-context :auth-claims] payload))))}))

(defmethod ig/init-key ::webserver [_ config]
  (ws/start (-> config
                http/default-interceptors
                (update ::http/interceptors #(conj % middlewares/cookies extract-auth-cookie set-auth-cookie)))))

(defmethod ig/halt-key! ::webserver [_ webserver]
  (ws/stop webserver))

;; ------------------- ;;
;; Lifecycle functions ;;
;; ------------------- ;;

(defn start
  []
  (u/set-global-context!
   {:app-name "demo" :env (:env env)})
  (ig/init system))

(defn stop
  [rag-service]
  (ig/halt! rag-service))

;; -------------- ;;
;; Runtime System ;;
;; -------------- ;;

(defonce app (atom {}))

(defn -main
  [& _]
  (reset! app (start))
  (u/log ::app-started)
  (.addShutdownHook (Runtime/getRuntime)
                    (Thread. #(do
                                (u/log ::stopping-app)
                                (stop @app)))))

(comment

  (def app (start))

  (stop app)



  "")