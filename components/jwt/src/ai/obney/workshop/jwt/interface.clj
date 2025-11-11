(ns ai.obney.workshop.jwt.interface
  (:require [ai.obney.workshop.jwt.core :as core]))

(defn sign
  "Sign a JWT with optional expiration time.
   
   Default expiration is 1 second if neither :does-not-expire or :expire-in is provided.
   
   Examples:
   
   (sign {:payload {:hello :world} 
          :does-not-expire true
          :secret \"foo\"})
   
   (sign {:payload {:hello :world}
          :secret \"foo\"
          :expire-in [10 :minutes]})
   
   unit options: 
     :nanos
     :micros
     :millis
     :seconds
     :minutes
     :hours
     :half-days
     :days
     :weeks
     :months
     :years
     :decades
     :centuries
     :millennia
     :eras
     :forever"
  [{[_n _unit :as _expire-in] :expire-in
    :keys [_payload _secret]
    :as args}]
  (core/sign args))

(defn unsign
  "Unsign and verify a JWT.
   
   Throws exception if invalid or expired.
   
   Example:
   
   (unsign {:token token :secret \"foo\"})"
  [{:keys [_token _secret] :as args}]
  (core/unsign args))


