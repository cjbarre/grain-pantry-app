(ns ai.obney.workshop.jwt.core
  (:require [buddy.sign.jwt :as jwt]
            [ai.obney.grain.time.interface :as time]
            [tick.core :as t]))

(defn sign
  [{[n unit :as expire-in] :expire-in
    :keys [payload secret does-not-expire]}]
  (try (jwt/sign
        (cond-> payload
          :iat (assoc :iat
                      (->> (time/now)
                           (t/instant)
                           (t/long)))

          :default-expiration (assoc :exp
                                     (-> (t/>> (time/now) (t/new-duration 1 :seconds))
                                         (t/instant)
                                         (t/long)))

          does-not-expire (dissoc :exp)

          expire-in (assoc :exp
                           (-> (t/>> (time/now) (t/new-duration n unit))
                               (t/instant)
                               (t/long))))
        secret)
       (catch Exception e
         (throw e))))

(defn unsign
  [{:keys [token secret]}]
  (jwt/unsign token secret))


(comment

  (def t
    (sign
     {:payload {:hello :world}
      :secret "cheese"
      :expire-in [10 :minutes]}))
  
  (unsign {:token t :secret "cheese"})


  ""
  )
