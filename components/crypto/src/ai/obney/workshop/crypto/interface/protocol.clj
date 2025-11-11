(ns ai.obney.workshop.crypto.interface.protocol)

(defprotocol CryptoProvider
  (encrypt [this args] "Encrypt data")
  (decrypt [this args] "Decrypt data"))