(ns ai.obney.workshop.ai-agent-service.core.read-models
  "Read models for AI agent event sourcing")

(defn conversation-read-model
  "Build conversation history from AI interaction events.

   Takes events and builds a conversation history in the format:
   {:conversation-history [{:role \"user\" :content \"...\"}
                           {:role \"assistant\" :content \"...\"}]}"
  [initial-state events]
  (reduce
    (fn [acc {:keys [event/type] :as event}]
      (case type
        :ai/question-asked
        (update acc :conversation-history (fnil conj [])
          {:role "user"
           :content (:question event)})

        :ai/response-generated
        (update acc :conversation-history (fnil conj [])
          {:role "assistant"
           :content (:response event)})

        ;; Default: pass through unchanged
        acc))
    initial-state
    events))
