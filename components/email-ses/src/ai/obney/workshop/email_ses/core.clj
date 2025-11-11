(ns ai.obney.workshop.email-ses.core
  (:require [ai.obney.workshop.email.interface.protocol :as p]
            [cognitect.aws.client.api :as aws]
            [com.brunobonacci.mulog :as u]))

(defn print-email-to-console
  "Prints email details to console for local development."
  [{:keys [to cc bcc from subject body-text body-html]}]
  (u/log ::email-sent-localstack-mock
         :from from
         :to to
         :cc cc
         :bcc bcc
         :subject subject
         :body-text body-text
         :body-html body-html)
  {:MessageId "mock-message-id-localstack"})

(defrecord SES [config]
  p/Email
  (send [_ {:keys [to cc bcc from subject body-text body-html] :as email-data}]
    (if (:localstack/enabled config)
      ;; LocalStack mode - print to console
      (print-email-to-console email-data)
      ;; Real AWS SES v2
      (let [ses (aws/client (merge {:api :sesv2} config))]
        (aws/invoke ses
                    {:op :SendEmail
                     :request {:FromEmailAddress from
                               :Destination {:ToAddresses to
                                             :CcAddresses  cc
                                             :BccAddresses bcc}
                               :Content {:Simple {:Subject {:Data subject}
                                                  :Body (cond-> {}
                                                          body-text (assoc :Text {:Data body-text})
                                                          body-html (assoc :Html {:Data body-html}))}}}})))))



  (comment
    ;; Example usage with LocalStack (prints to console)
    (def ses (->SES {:localstack/enabled true}))

    (p/send ses {:to ["test@example.com"]
                 :from "noreply@demo.net"
                 :subject "Hello there!"
                 :body-text "TEST!"})

    "")
