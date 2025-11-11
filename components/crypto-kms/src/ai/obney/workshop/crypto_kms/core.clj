(ns ai.obney.workshop.crypto-kms.core
  (:require [ai.obney.workshop.crypto.interface.protocol :as p]
            [cognitect.aws.client.api :as aws]
            [buddy.core.crypto :as crypto]
            [buddy.core.codecs :as codecs]
            [buddy.core.nonce :as nonce]
            [clojure.java.io :as io]
            [com.brunobonacci.mulog :as u]))

(defn- make-kms-client
  "Creates a KMS client with optional LocalStack endpoint override."
  [config]
  (let [base-config {:api :kms}
        client-config (if (and (:localstack/enabled config)
                               (:localstack/endpoint config))
                        (merge base-config
                               {:region (:aws/region config "us-east-1")
                                :endpoint-override {:protocol :http
                                                    :hostname "localhost"
                                                    :port 4566}})
                        (if (:aws/region config)
                          (merge base-config {:region (:aws/region config)})
                          base-config))]
    (aws/client client-config)))

(defn read-buffered-input-stream
  [^java.io.BufferedInputStream in]
  (with-open [in in
              out (java.io.ByteArrayOutputStream.)]
    (io/copy in out)
    (.toByteArray out)))

(defn encrypt
  "Encrypts plaintext with a fresh KMS-generated data key using AES-GCM.
   Returns a map: {:ciphertext, :encrypted-key, :iv} — all hex-encoded strings."
  [{{:keys [kms-key-id] :as config} :config :as _crypto-provider} {:keys [plaintext]}]
  (let [kms (make-kms-client config)
        {:keys [Plaintext CiphertextBlob]}
        (aws/invoke kms {:op :GenerateDataKey
                         :request {:KeyId kms-key-id
                                   :KeySpec "AES_256"}})
        cipher-text-blob-bytes (read-buffered-input-stream CiphertextBlob)
        data-key-bytes (read-buffered-input-stream Plaintext)
        iv-bytes        (nonce/random-bytes 12) ;; 96-bit IV, required for GCM
        ciphertext-bytes (crypto/encrypt
                          (.getBytes plaintext "UTF-8")
                          data-key-bytes
                          iv-bytes
                          {:alg :aes256-gcm})]
    {:ciphertext   (codecs/bytes->hex ciphertext-bytes)
     :encrypted-key (codecs/bytes->hex cipher-text-blob-bytes)
     :iv            (codecs/bytes->hex iv-bytes)}))

(defn decrypt
  "Decrypts encrypted blob with KMS-wrapped data key and IV.
   Accepts a map with :ciphertext, :encrypted-key, :iv (hex strings).
   Returns original plaintext string."
  [{:keys [config] :as _crypto-provider} {:keys [ciphertext encrypted-key iv]}]
  (let [kms (make-kms-client config)
        resp (aws/invoke kms {:op :Decrypt
                              :request {:CiphertextBlob (codecs/hex->bytes encrypted-key)}})
        data-key (read-buffered-input-stream (:Plaintext resp))
        plaintext-bytes (crypto/decrypt
                         (codecs/hex->bytes ciphertext)
                         data-key
                         (codecs/hex->bytes iv)
                         {:alg :aes256-gcm})]
    (String. plaintext-bytes "UTF-8")))

(defrecord KmsCryptoProvider [config]
  p/CryptoProvider
  (encrypt [this args] (encrypt this args))
  (decrypt [this args] (decrypt this args)))

(comment
  ;; Example usage with LocalStack
  (def provider (->KmsCryptoProvider {:kms-key-id "alias/grain-local-key"
                                      :localstack/enabled true
                                      :localstack/endpoint "http://localhost:4566"
                                      :aws/region "us-east-1"}))

  (encrypt provider {:plaintext "Hello, World!"})

  (decrypt provider
           {:ciphertext "3dd71ddaf226444289009a84023b9c9b4585e6e0a8a822b86597e7b193",
            :encrypted-key
            "0102030078b146addda4ce904ebcfc2c3fd62349edf797c12fc38d15115f51da8b6f73cef901f88bff8b3b7eb03137eeec95fbe8bfa80000007e307c06092a864886f70d010706a06f306d020100306806092a864886f70d010701301e060960864801650304012e3011040ccaf7d22d082eb31b66551901020110803b8aff10073bfe83cda08241ac1af744aebac4da1b8ed37906cd79228fcc8c76a9cbac1ad7c4929e6658100bf48ae487d726340d126fc03496f8a3c7",
            :iv "662c49d34047a0ad5697f960"})

  ""
  )