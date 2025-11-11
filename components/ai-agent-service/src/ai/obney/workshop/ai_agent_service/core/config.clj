(ns ai.obney.workshop.ai-agent-service.core.config
  "DSPy configuration for AI agent service"
  (:require [libpython-clj2.require :refer [require-python]]))

(require-python '[dspy :as dspy])

(defn initialize-dspy!
  "Initialize DSPy with LLM configuration from environment variables.

   Required environment variables:
   - OPENROUTER_API_KEY or OPENAI_API_KEY

   Optional environment variables:
   - LLM_PROVIDER (default: openai/gpt-4o-mini)
   - OPENROUTER_API_BASE (default: https://openrouter.ai/api/v1)"
  []
  (let [provider (or (System/getenv "LLM_PROVIDER") "openai/gpt-4o-mini")
        api-key (or (System/getenv "OPENROUTER_API_KEY")
                    (System/getenv "OPENAI_API_KEY"))
        api-base (or (System/getenv "OPENROUTER_API_BASE")
                     "https://openrouter.ai/api/v1")]

    (when-not api-key
      (throw (ex-info "No LLM API key found. Set OPENROUTER_API_KEY or OPENAI_API_KEY environment variable"
                      {:provider provider})))

    (let [lm (if (System/getenv "OPENROUTER_API_KEY")
               ;; OpenRouter configuration
               #_{:clj-kondo/ignore [:unresolved-namespace]}
               (dspy/LM provider
                 :api_key api-key
                 :api_base api-base
                 :cache false)
               ;; Direct OpenAI configuration
               #_{:clj-kondo/ignore [:unresolved-namespace]}
               (dspy/LM provider
                 :api_key api-key
                 :cache false))]

      #_{:clj-kondo/ignore [:unresolved-namespace]}
      (dspy/configure :lm lm)

      (println (str "✅ DSPy configured with provider: " provider))
      {:provider provider
       :api-base (when (System/getenv "OPENROUTER_API_KEY") api-base)})))

(defn configured?
  "Check if DSPy has been configured"
  []
  (try
    #_{:clj-kondo/ignore [:unresolved-namespace]}
    (some? (dspy/settings :lm))
    (catch Exception _ false)))
