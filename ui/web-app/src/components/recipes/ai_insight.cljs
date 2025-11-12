(ns components.recipes.ai-insight
  "AI insight badge and reasoning display for recipe cards"
  (:require [uix.core :refer [defui $]]
            ["/gen/shadcn/components/ui/badge" :as badge]))

(defui ai-insight
  "Display AI reasoning for a recipe suggestion.

   Props:
   - reasoning: AI-generated reasoning text (string)
   - match-score: Optional match score (int 0-100)"
  [{:keys [reasoning match-score]}]
  (when reasoning
    ($ :div {:class "mt-3 p-3 border border-primary/20 bg-primary/5 hover:bg-primary/10 transition-colors"}
       ;; Header with badge and score
       ($ :div {:class "flex items-center justify-between gap-2 mb-2"}
          ($ badge/Badge {:variant "outline"
                          :class "text-xs border-primary/40 text-primary"}
             " AI Insight")

          (when match-score
            ($ :div {:class "text-xs text-muted-foreground"}
               (str "Match: " match-score "%"))))

       ;; Reasoning text
       ($ :p {:class "text-sm text-foreground/90 leading-relaxed"}
          reasoning))))
