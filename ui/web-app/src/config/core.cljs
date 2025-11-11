(ns config.core)

(goog-define API_BASE_URL "http://localhost:8081")

(defn api-base-url []
  API_BASE_URL)