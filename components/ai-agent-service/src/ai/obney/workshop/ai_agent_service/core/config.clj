(ns ai.obney.workshop.ai-agent-service.core.config
  "DSPy configuration utilities for AI agent service"
  (:require [libpython-clj2.require :refer [require-python]]))

(require-python '[dspy :as dspy])

(defn configured?
  "Check if DSPy has been configured.

   DSPy is initialized automatically by Integrant when the service starts.
   This function is used to verify configuration before executing commands."
  []
  (try
    (some? (dspy/settings :lm))
    (catch Exception _ false)))
