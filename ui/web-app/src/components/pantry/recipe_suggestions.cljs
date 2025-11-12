(ns components.pantry.recipe-suggestions
  (:require [uix.core :as uix :refer [defui $]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/progress" :as progress]
            ["lucide-react" :refer [Check X ShoppingCart Clock ChefHat Sparkles ThumbsDown]]
            [components.context.interface :as context]
            [components.recipes.ai-insight :as ai-insight]
            [store.recipes.events :as recipe-events]
            [store.recipes.subs :as recipe-subs]))

(defui ingredient-list [{:keys [ingredients]}]
  ($ :ul {:class "space-y-2 text-sm"}
     (for [ing ingredients]
       ($ :li {:key (:name ing)
              :class "flex items-center gap-2"}
          (if (:have ing)
            ($ Check {:size 16 :class "text-primary flex-shrink-0"})
            ($ X {:size 16 :class "text-destructive flex-shrink-0"}))
          ($ :span {:class (when-not (:have ing) "text-muted-foreground")}
             (:name ing))))))

(defui recipe-card [{:keys [recipe api-client]}]
  (let [{:keys [title time difficulty ingredients match-percent match-score
                ai-reasoning id url]} recipe

        ;; Calculate ingredient stats
        have-count (if ingredients
                    (count (filter :have ingredients))
                    0)
        total-count (if ingredients (count ingredients) 0)
        missing-count (- total-count have-count)
        can-make? (zero? missing-count)

        ;; Use match-score (from AI) or match-percent (from existing logic)
        display-match (or match-score match-percent 0)
        is-ai-recipe? (boolean ai-reasoning)]

    ($ card/Card {:class "flex flex-col hover:shadow-lg transition-shadow"
                  :on-click #(when (and api-client (:id recipe))
                              (rf/dispatch [::recipe-events/track-view recipe api-client]))}
       ($ card/CardHeader
          ($ :div {:class "flex items-start justify-between gap-2"}
             ($ :div {:class "flex-1"}
                ($ :div {:class "flex items-center gap-2 mb-2"}
                   ($ card/CardTitle {:class "text-xl"} title)
                   (when is-ai-recipe?
                     ($ Sparkles {:size 16 :class "text-primary"})))

                (when (or time difficulty)
                  ($ :div {:class "flex items-center gap-3 text-sm text-muted-foreground"}
                     (when time
                       ($ :div {:class "flex items-center gap-1"}
                          ($ Clock {:size 14})
                          time))
                     (when (and time difficulty) ($ :span "•"))
                     (when difficulty ($ :span difficulty)))))

             (if can-make?
               ($ badge/Badge {:variant "default" :class "bg-primary"}
                  ($ ChefHat {:size 14 :class "mr-1"})
                  "Ready!")
               (when (> total-count 0)
                 ($ badge/Badge {:variant "outline"}
                    "Missing: " missing-count)))))

       ($ card/CardContent {:class "flex-1"}
          ;; Match indicator (only if we have ingredients)
          (when (> total-count 0)
            ($ :div {:class "mb-4"}
               ($ :div {:class "flex items-center justify-between text-sm mb-2"}
                  ($ :span {:class "font-medium"}
                     have-count " / " total-count " ingredients")
                  ($ :span {:class "text-muted-foreground"}
                     display-match "%"))
               ($ progress/Progress {:value display-match :class "h-2"})))

          ;; Ingredients list (if available)
          (when (seq ingredients)
            ($ ingredient-list {:ingredients ingredients}))

          ;; AI Insight (for AI-suggested recipes)
          (when ai-reasoning
            ($ ai-insight/ai-insight {:reasoning ai-reasoning
                                      :match-score match-score})))

       ($ card/CardFooter {:class "flex gap-2"}
          (if url
            ;; External recipe - open in new tab
            ($ button/Button {:variant "default"
                              :class "flex-1"
                              :on-click #(do
                                          (.stopPropagation %)
                                          (when url
                                            (js/window.open url "_blank")))}
               ($ ChefHat {:size 16 :class "mr-2"})
               "View Recipe")
            ;; Internal recipe - placeholder
            ($ button/Button {:variant "default" :class "flex-1"}
               ($ ChefHat {:size 16 :class "mr-2"})
               "View Recipe"))

          ;; Tracking buttons
          (when is-ai-recipe?
            ($ button/Button {:variant "ghost"
                              :size "icon"
                              :title "Not interested"
                              :on-click #(do
                                          (.stopPropagation %)
                                          (rf/dispatch [::recipe-events/track-dismiss recipe api-client]))}
               ($ ThumbsDown {:size 16})))

          (when-not can-make?
            ($ button/Button {:variant "outline"
                              :title "Add missing items to shopping list"
                              :on-click #(do
                                          (.stopPropagation %)
                                          (rf/dispatch [::recipe-events/add-missing-items-to-shopping recipe api-client]))}
               ($ ShoppingCart {:size 16})))))))

(defui view []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Subscribe to AI recipes
        ai-recipes (use-subscribe [::recipe-subs/ai-recipes])
        ai-loading (use-subscribe [::recipe-subs/loading])

        ;; Use only AI recipes
        all-recipes (or ai-recipes [])

        sorted-recipes (sort-by #(or (:match-score %) (:match-percent %) 0) > all-recipes)]
    ($ :div {:class "space-y-6"}
       ;; Header with stats and AI search button
       ($ card/Card
          ($ card/CardContent {:class "pt-6"}
             ($ :div {:class "grid grid-cols-1 md:grid-cols-4 gap-4"}
                ;; AI Search button
                ($ :div {:class "md:col-span-1 flex items-center justify-center"}
                   ($ button/Button {:variant "default"
                                     :class "w-full"
                                     :disabled ai-loading
                                     :on-click #(when api-client
                                                 (rf/dispatch [::recipe-events/fetch-ai-recipes api-client]))}
                      ($ Sparkles {:size 16 :class "mr-2"})
                      (if ai-loading "Searching..." "AI Recipe Search")))

                ;; Stats
                ($ :div {:class "md:col-span-3 grid grid-cols-3 gap-4 text-center"}
                   ($ :div
                      ($ :div {:class "text-3xl font-bold text-primary"}
                         (count (filter #(>= (or (:match-score %) (:match-percent %) 0) 80) all-recipes)))
                      ($ :p {:class "text-sm text-muted-foreground"} "Ready to Cook"))
                   ($ :div
                      ($ :div {:class "text-3xl font-bold"}
                         (count (filter #(let [score (or (:match-score %) (:match-percent %) 0)]
                                          (and (< score 80) (>= score 50)))
                                       all-recipes)))
                      ($ :p {:class "text-sm text-muted-foreground"} "Almost Ready"))
                   ($ :div
                      ($ :div {:class "text-3xl font-bold"}
                         (count all-recipes))
                      ($ :p {:class "text-sm text-muted-foreground"} "Total Recipes"))))))

       ;; Recipe cards grid
       ($ :div {:class "grid grid-cols-1 lg:grid-cols-2 gap-6"}
          (map-indexed
           (fn [idx recipe]
             ($ recipe-card {:key (str "recipe-" idx "-" (:id recipe))
                            :recipe recipe
                            :api-client api-client}))
           sorted-recipes)))))
