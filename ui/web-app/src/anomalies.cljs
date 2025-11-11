(ns anomalies)

(defn anomaly? [x] (when (::category x) x))