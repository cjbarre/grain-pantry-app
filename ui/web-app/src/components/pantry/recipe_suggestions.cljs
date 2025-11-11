(ns components.pantry.recipe-suggestions
  (:require [uix.core :as uix :refer [defui $ use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/progress" :as progress]
            ["lucide-react" :refer [Check X ShoppingCart Clock ChefHat]]
            [components.context.interface :as context]
            [store.pantry.events :as pantry-events]
            [store.pantry.subs :as pantry-subs]))

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

(defui recipe-card [{:keys [title time difficulty ingredients match-percent]}]
  (let [have-count (count (filter :have ingredients))
        total-count (count ingredients)
        missing-count (- total-count have-count)
        can-make? (zero? missing-count)]

    ($ card/Card {:class "flex flex-col"}
       ($ card/CardHeader
          ($ :div {:class "flex items-start justify-between gap-2"}
             ($ :div {:class "flex-1"}
                ($ card/CardTitle {:class "text-xl mb-2"} title)
                ($ :div {:class "flex items-center gap-3 text-sm text-muted-foreground"}
                   ($ :div {:class "flex items-center gap-1"}
                      ($ Clock {:size 14})
                      time)
                   ($ :span "•")
                   ($ :span difficulty)))
             (if can-make?
               ($ badge/Badge {:variant "default" :class "bg-primary"}
                  ($ ChefHat {:size 14 :class "mr-1"})
                  "Ready!")
               ($ badge/Badge {:variant "outline"}
                  "Missing: " missing-count))))

       ($ card/CardContent {:class "flex-1"}
          ;; Match indicator
          ($ :div {:class "mb-4"}
             ($ :div {:class "flex items-center justify-between text-sm mb-2"}
                ($ :span {:class "font-medium"}
                   have-count " / " total-count " ingredients")
                ($ :span {:class "text-muted-foreground"}
                   match-percent "%"))
             ($ progress/Progress {:value match-percent :class "h-2"}))

          ;; Ingredients list
          ($ ingredient-list {:ingredients ingredients}))

       ($ card/CardFooter {:class "flex gap-2"}
          ($ button/Button {:variant "default" :class "flex-1"}
             ($ ChefHat {:size 16 :class "mr-2"})
             "View Recipe")
          (when-not can-make?
            ($ button/Button {:variant "outline"}
               ($ ShoppingCart {:size 16})))))))

(defui view []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Subscribe to recipes from store
        recipes (use-subscribe [::pantry-subs/recipes])

        ;; Fetch recipe matches on mount
        _ (use-effect
            (fn []
              (when api-client
                (rf/dispatch [::pantry-events/fetch-recipe-matches api-client]))
              (fn []))
            [api-client])

        sorted-recipes (sort-by :match-percent > recipes)]
    ($ :div {:class "space-y-6"}
       ;; Header with stats
       ($ card/Card
          ($ card/CardContent {:class "pt-6"}
             ($ :div {:class "grid grid-cols-1 md:grid-cols-3 gap-4 text-center"}
                ($ :div
                   ($ :div {:class "text-3xl font-bold text-primary"}
                      (count (filter #(>= (:match-percent %) 80) recipes)))
                   ($ :p {:class "text-sm text-muted-foreground"} "Ready to Cook"))
                ($ :div
                   ($ :div {:class "text-3xl font-bold"}
                      (count (filter #(and (< (:match-percent %) 80)
                                          (>= (:match-percent %) 50))
                                    recipes)))
                   ($ :p {:class "text-sm text-muted-foreground"} "Almost Ready"))
                ($ :div
                   ($ :div {:class "text-3xl font-bold"}
                      (count recipes))
                   ($ :p {:class "text-sm text-muted-foreground"} "Total Recipes")))))

       ;; Recipe cards grid
       ($ :div {:class "grid grid-cols-1 lg:grid-cols-2 gap-6"}
          (for [recipe sorted-recipes]
            ($ recipe-card {:key (:id recipe)
                           :title (:title recipe)
                           :time (:time recipe)
                           :difficulty (:difficulty recipe)
                           :ingredients (:ingredients recipe)
                           :match-percent (:match-percent recipe)}))))))
