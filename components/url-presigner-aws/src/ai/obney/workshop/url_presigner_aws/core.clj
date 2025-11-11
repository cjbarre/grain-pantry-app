(ns ai.obney.workshop.url-presigner-aws.core
  (:require [clojure.string :as str]
            [ai.obney.workshop.url-presigner.interface.protocol :as p])
  (:import [software.amazon.awssdk.auth.credentials DefaultCredentialsProvider AwsBasicCredentials StaticCredentialsProvider]
           [software.amazon.awssdk.regions Region]
           [software.amazon.awssdk.services.s3.presigner.model
            PresignedPutObjectRequest
            PutObjectPresignRequest
            PresignedGetObjectRequest
            GetObjectPresignRequest]
           [software.amazon.awssdk.services.s3.model PutObjectRequest GetObjectRequest]
           [software.amazon.awssdk.services.s3.presigner S3Presigner]
           [software.amazon.awssdk.services.s3 S3Configuration]
           [java.net URI]))

(defn format-s3-bucket-key
  [a b]
  (format "%s/%s" a b))

(defn get-s3-presigner
  [{:keys [credentials-provider region localstack-endpoint]}]
  (let [builder (S3Presigner/builder)]
    (.credentialsProvider builder credentials-provider)
    (.region builder region)
    (when localstack-endpoint
      (.endpointOverride builder (URI. localstack-endpoint))
      ;; Use path-style access for LocalStack (http://localhost:4566/bucket/key)
      ;; instead of virtual-hosted style (http://bucket.localhost:4566/key)
      (.serviceConfiguration builder
        (-> (S3Configuration/builder)
            (.pathStyleAccessEnabled true)
            (.build))))
    (.build builder)))

(defn presign-put-object-presign-request
  [{:keys [s3-presigner]} put-object-presign-request]
  (.presignPutObject ^S3Presigner s3-presigner
                     ^PresignedPutObjectRequest put-object-presign-request))

(defn create-put-object-presign-request
  [{:keys [signature-duration-minutes]} ^PutObjectRequest put-object-request]
  (.. (doto
       (PutObjectPresignRequest/builder)
        (.signatureDuration (java.time.Duration/ofMinutes signature-duration-minutes))
        (.putObjectRequest put-object-request))
      (build)))

(defn create-put-object-request
  [{:keys [s3-bucket s3-key s3-object-name content-type]}]
  (.. (doto
       (PutObjectRequest/builder)
        (.bucket s3-bucket)
        (.key (format-s3-bucket-key s3-key s3-object-name))
        (.contentType content-type))
      (build)))

(defn get-process-fn
  [{:keys [s3-presigner signature-duration-minutes] :as params}]
  (comp (partial presign-put-object-presign-request
                 (select-keys params [:s3-presigner]))
        (partial create-put-object-presign-request
                 (select-keys params [:signature-duration-minutes]))
        create-put-object-request))

(defn presign-put-url
  [{:keys [s3-bucket
           s3-key
           signature-duration-minutes
           aws-region
           _content-type
           s3-object-name
           localstack/enabled
           localstack/endpoint] :as params}]
  (let [credentials-provider (if enabled
                                (StaticCredentialsProvider/create
                                 (AwsBasicCredentials/create "test" "test"))
                                (DefaultCredentialsProvider/create))
        localstack-endpoint (when enabled endpoint)]
    (with-open [s3-presigner (get-s3-presigner
                              {:credentials-provider credentials-provider
                               :region aws-region
                               :localstack-endpoint localstack-endpoint})]
      (let [process-fn (get-process-fn {:s3-presigner s3-presigner
                                        :signature-duration-minutes signature-duration-minutes})
            presigned-url (-> params
                              process-fn
                              .url
                              str)]
        {:url presigned-url
         :expiration-minutes signature-duration-minutes
         :bucket s3-bucket
         :key (format-s3-bucket-key s3-key s3-object-name)}))))

(defn presign-get-object-presign-request
  [{:keys [s3-presigner]} get-object-presign-request]
  (.presignGetObject ^S3Presigner s3-presigner
                     ^PresignedGetObjectRequest get-object-presign-request))

(defn create-get-object-presign-request
  [{:keys [signature-duration-minutes]} ^GetObjectRequest get-object-request]
  (.. (doto
       (GetObjectPresignRequest/builder)
        (.signatureDuration (java.time.Duration/ofMinutes signature-duration-minutes))
        (.getObjectRequest get-object-request))
      (build)))

(defn create-get-object-request
  [{:keys [s3-bucket s3-key]}]
  (.. (doto
       (GetObjectRequest/builder)
        (.bucket s3-bucket)
        (.key s3-key))
      (build)))

(defn presign-get-url
  [{:keys [_s3-bucket
           _s3-key
           _signature-duration-minutes
           aws-region
           localstack/enabled
           localstack/endpoint] :as params}]
  (let [credentials-provider (if enabled
                                (StaticCredentialsProvider/create
                                 (AwsBasicCredentials/create "test" "test"))
                                (DefaultCredentialsProvider/create))
        localstack-endpoint (when enabled endpoint)]
    (with-open [s3-presigner (get-s3-presigner
                              {:credentials-provider credentials-provider
                               :region aws-region
                               :localstack-endpoint localstack-endpoint})]
      (let [params (assoc params :s3-presigner s3-presigner)
            url (->> (create-get-object-request params)
                     (create-get-object-presign-request params)
                     (presign-get-object-presign-request params)
                     .url
                     str)]
        (-> params
            (assoc :url url)
            (dissoc :s3-presigner))))))

(defn parse-s3-url
  [url]
  (let [[bucket :as xs] (-> url
                            (str/split #"//")
                            second
                            (str/split #"/"))]
    {:s3-bucket bucket
     :s3-key (str/join "/" (rest xs))}))

(defn s3-url->presigned-url
  [s3-url signature-duration-minutes]
  (-> s3-url
      parse-s3-url
      (assoc :signature-duration-minutes
             signature-duration-minutes)
      presign-get-url
      :url))

(defrecord URLPresignerAWS [config]
  p/URLPresigner
  (presign-get [_ args]
    (presign-get-url (merge config args)))
  (presign-put [_ args]
    (presign-put-url (merge config args))))

(comment
  ;; Example usage with LocalStack
  (require '[clj-http.client :as http])

  (def presigner (->URLPresignerAWS
                  {:aws-region Region/US_EAST_1
                   :localstack/enabled true
                   :localstack/endpoint "http://localhost:4566"}))

  (def file-content "TEST!")

  (try
    (http/put
     (-> (p/presign-put
          presigner
          {:s3-bucket "grain-files"
           :s3-key "test"
           :s3-object-name "test.txt"
           :content-type "text/plain"
           :signature-duration-minutes 30})
         :url)
     {:body (.getBytes file-content)
      :headers {"Content-Type" "text/plain"}})
    (catch Exception e (ex-data e)))

  (-> (http/get
       (-> (p/presign-get
            presigner
            {:s3-bucket "grain-files"
             :s3-key "test/test.txt"
             :signature-duration-minutes 30})
           :url)
       {:as :byte-array})
      :body
      String.)

  ""
  )