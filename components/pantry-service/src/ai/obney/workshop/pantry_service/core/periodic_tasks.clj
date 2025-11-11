(ns ai.obney.workshop.pantry-service.core.periodic-tasks
  "Periodic tasks for pantry-service.

   These tasks run on a schedule and can perform housekeeping,
   send notifications, or trigger other actions."
  (:require [ai.obney.workshop.pantry-service.core.read-models :as rm]
            [ai.obney.grain.event-store-v2.interface :as es]
            [com.brunobonacci.mulog :as u]))

(defn check-expiring-items
  "Daily task to check for items expiring soon and log warnings"
  [{:keys [event-store] :as _context} _time]
  ;; NOTE: Currently fetches ALL pantry events across all households
  ;; TODO: Optimize by querying each household separately with tags if household list available
  ;;       e.g., (es/read event-store {:types rm/pantry-event-types :tags #{[:household household-id]}})
  (let [all-items (->> (es/read event-store {:types rm/pantry-event-types})
                       (rm/apply-pantry-events)
                       (vals))
        now (java.time.LocalDate/now)
        expiring-soon (->> all-items
                          (filter :expires)
                          (keep (fn [item]
                                  (when-let [expires-str (:expires item)]
                                    (try
                                      (let [expires-date (java.time.LocalDate/parse expires-str)
                                            days-until (.until now expires-date java.time.temporal.ChronoUnit/DAYS)]
                                        (when (and (>= days-until 0)
                                                  (<= days-until 7))
                                          {:household-id (:household-id item)
                                           :name (:name item)
                                           :expires expires-str
                                           :days-until days-until}))
                                      (catch Exception _ nil))))))]
    (when (seq expiring-soon)
      (u/log ::items-expiring-soon
             :count (count expiring-soon)
             :items expiring-soon))
    ;; TODO: Send notifications to household members
    ;; Could trigger commands to send emails or push notifications
    ))
