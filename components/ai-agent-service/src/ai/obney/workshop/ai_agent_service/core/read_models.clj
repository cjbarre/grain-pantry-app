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

(defn preference-signals-read-model
  "Build user recipe preference signals from interaction events.

   Tracks:
   - Viewed recipes (user clicked on recipe)
   - Cooked recipes (user marked as cooked)
   - Dismissed recipes (user dismissed recipe)

   Returns map with:
   {:viewed-recipes [recipe-id ...]
    :cooked-recipes [recipe-id ...]
    :dismissed-recipes [recipe-id ...]}"
  [initial-state events]
  (reduce
    (fn [acc {:keys [event/type event/body] :as event}]
      (case type
        :ai/recipe-viewed
        (update acc :viewed-recipes (fnil conj [])
          (:recipe-id body))

        :ai/recipe-marked-cooked
        (update acc :cooked-recipes (fnil conj [])
          (:recipe-id body))

        :ai/recipe-dismissed
        (update acc :dismissed-recipes (fnil conj [])
          (:recipe-id body))

        ;; Default: pass through unchanged
        acc))
    (merge {:viewed-recipes []
            :cooked-recipes []
            :dismissed-recipes []}
           initial-state)
    events))
