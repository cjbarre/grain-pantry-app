(ns ai.obney.workshop.url-presigner.interface.protocol)

(defprotocol URLPresigner
  (presign-get [this args])
  (presign-put [this args]))