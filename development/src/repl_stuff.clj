(ns repl-stuff
  (:require [ai.obney.workshop.web-api.core :as service]
            [ai.obney.grain.event-store-v2.interface :as es]
            [ai.obney.grain.query-processor.interface :as qp]
            [ai.obney.grain.command-processor.interface :as cp]
            [ai.obney.grain.time.interface :as time]))


(comment

  ;;
  ;; Start Service
  ;;
  (do
    (def service (service/start))
    (def context (::service/context service))
    (def event-store (:event-store context)))

  ;;
  ;; Stop Service ;;
  ;;
  (service/stop service)


  (into [] (es/read event-store {}))


  (qp/process-query
   (assoc context 
          :auth-claims {:household-id #uuid "ec596841-c9de-414d-9aec-6761dee8e84d"}
          :query 
          {:query/id (random-uuid)
           :query/timestamp (time/now)
           :query/name :pantry/get-items
           :category nil
           :search nil}))

  "")