(ns ai.obney.workshop.url-presigner.interface
  (:require [ai.obney.workshop.url-presigner.interface.protocol :as p]))

(defn presign-get
  [url-presigner args]
  (p/presign-get url-presigner args))

(defn presign-put
  [url-presigner args]
  (p/presign-put url-presigner args))