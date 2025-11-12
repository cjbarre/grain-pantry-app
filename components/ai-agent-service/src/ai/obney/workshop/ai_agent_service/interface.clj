(ns ai.obney.workshop.ai-agent-service.interface
  "Public interface for AI agent service"
  (:require [ai.obney.workshop.ai-agent-service.core.config :as config]
            [ai.obney.workshop.ai-agent-service.core.commands :as commands]))

;; Configuration utilities
;; Note: DSPy is automatically initialized by Integrant when the service starts
(def configured? config/configured?)

;; Command registry
(def commands commands/commands)
