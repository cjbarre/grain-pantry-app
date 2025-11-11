(ns components.pantry.shopping-list
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/checkbox" :as checkbox]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["lucide-react" :refer [Plus Package Trash2 Check]]
            [components.context.interface :as context]
            [store.pantry.events :as pantry-events]
            [store.pantry.subs :as pantry-subs]))

(defui shopping-item [{:keys [name category quantity checked for-recipe on-toggle on-remove]}]
  ($ :div {:class "flex items-center gap-3 p-3 border-2 border-border rounded"}
     ($ checkbox/Checkbox {:checked checked
                 :on-checked-change on-toggle})
     ($ :div {:class "flex-1"}
        ($ :div {:class "flex items-center gap-2"}
           ($ :span {:class (str "font-medium "
                                (when checked "line-through text-muted-foreground"))}
              name)
           ($ badge/Badge {:variant "outline" :class "text-xs"}
              category))
        ($ :div {:class "text-sm text-muted-foreground mt-1"}
           quantity
           (when for-recipe
             ($ :span " • for " for-recipe))))
     ($ button/Button {:variant "ghost"
               :size "icon-sm"
               :on-click on-remove}
        ($ Trash2 {:size 16}))))

(defui view []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Subscribe to shopping list from store
        items (use-subscribe [::pantry-subs/shopping-list])

        ;; State for adding new items
        [new-item-name set-new-item-name] (use-state "")

        ;; Fetch shopping list on mount
        _ (use-effect
            (fn []
              (when api-client
                (rf/dispatch [::pantry-events/fetch-shopping-list api-client]))
              (fn []))
            [api-client])

        grouped-items (group-by :category items)
        completed-count (count (filter :checked items))
        total-count (count items)
        completed-item-ids (->> items
                                (filter :checked)
                                (mapv :id))]

    ($ :div {:class "space-y-6"}
       ;; Stats header
       ($ card/Card
          ($ card/CardContent {:class "pt-6"}
             ($ :div {:class "flex items-center justify-between"}
                ($ :div
                   ($ :div {:class "text-2xl font-bold"}
                      completed-count " / " total-count)
                   ($ :p {:class "text-sm text-muted-foreground"} "Items completed"))
                ($ :div {:class "flex gap-2"}
                   ($ button/Button {:variant "outline"
                                     :disabled (zero? completed-count)
                                     :on-click #(when (pos? completed-count)
                                                  (rf/dispatch [::pantry-events/move-to-pantry
                                                               completed-item-ids
                                                               api-client]))}
                      ($ Check {:size 16 :class "mr-2"})
                      "Add to Pantry")
                   ($ button/Button {:variant "outline"
                                     :disabled (zero? completed-count)
                                     :on-click #(when (pos? completed-count)
                                                  (rf/dispatch [::pantry-events/clear-completed api-client]))}
                      ($ Trash2 {:size 16 :class "mr-2"})
                      "Clear Completed")))))

       ;; Quick add
       ($ card/Card
          ($ card/CardContent {:class "pt-6"}
             ($ :div {:class "flex gap-2"}
                ($ :input {:type "text"
                          :placeholder "Add item to shopping list..."
                          :class "flex-1 h-9 px-3 border-2 border-border rounded text-sm"
                          :value new-item-name
                          :on-change #(set-new-item-name (.. % -target -value))
                          :on-key-down #(when (= (.-key %) "Enter")
                                         (when (not-empty (clojure.string/trim new-item-name))
                                           (rf/dispatch [::pantry-events/add-shopping-item
                                                        {:name (clojure.string/trim new-item-name)
                                                         :quantity "1"
                                                         :category "Groceries"
                                                         :for-recipe nil}
                                                        api-client
                                                        (fn [_]
                                                          (set-new-item-name "")
                                                          (rf/dispatch [::pantry-events/fetch-shopping-list api-client]))
                                                        nil])))})
                ($ button/Button
                   {:disabled (empty? (clojure.string/trim new-item-name))
                    :on-click #(when (not-empty (clojure.string/trim new-item-name))
                                 (rf/dispatch [::pantry-events/add-shopping-item
                                              {:name (clojure.string/trim new-item-name)
                                               :quantity "1"
                                               :category "Groceries"
                                               :for-recipe nil}
                                              api-client
                                              (fn [_]
                                                (set-new-item-name "")
                                                (rf/dispatch [::pantry-events/fetch-shopping-list api-client]))
                                              nil]))}
                   ($ Plus {:size 16 :class "mr-2"})
                   "Add"))))

       ;; Shopping list by category
       (if (or (nil? items) (empty? items))
         ($ card/Card
            ($ card/CardContent {:class "py-12 text-center"}
               ($ Package {:size 48 :class "mx-auto mb-4 text-muted-foreground"})
               ($ :p {:class "text-lg font-medium mb-2"} "Your shopping list is empty")
               ($ :p {:class "text-sm text-muted-foreground"}
                  "Add items from recipes or manually add them above")))

         ($ :div {:class "space-y-4"}
            (for [[category category-items] grouped-items]
              ($ card/Card {:key category}
                 ($ card/CardHeader
                    ($ card/CardTitle {:class "text-lg"}
                       category
                       ($ badge/Badge {:variant "outline" :class "ml-2"}
                          (count category-items))))
                 ($ card/CardContent
                    ($ :div {:class "space-y-2"}
                       (for [item category-items]
                         ($ shopping-item
                            {:key (:id item)
                             :id (:id item)
                             :name (:name item)
                             :category (:category item)
                             :quantity (:quantity item)
                             :checked (:checked item)
                             :for-recipe (:for-recipe item)
                             :on-toggle #(rf/dispatch [::pantry-events/toggle-shopping-item
                                                       (:id item)
                                                       (not (:checked item))
                                                       api-client])
                             :on-remove #(rf/dispatch [::pantry-events/remove-shopping-item
                                                       (:id item)
                                                       api-client])})))))))))))
