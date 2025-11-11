(ns ai.obney.workshop.ai-agent-service.interface
  "Public interface for AI agent service"
  (:require [ai.obney.workshop.ai-agent-service.core.config :as config]
            [ai.obney.workshop.ai-agent-service.core.commands :as commands]))

;; Configuration
(def initialize-dspy! config/initialize-dspy!)
(def configured? config/configured?)

;; Command registry
(def commands commands/commands)
