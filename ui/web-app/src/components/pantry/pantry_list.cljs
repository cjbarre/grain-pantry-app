(ns components.pantry.pantry-list
  (:require [uix.core :as uix :refer [defui $ use-state use-effect]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/input" :as input]
            ["lucide-react" :refer [Plus Package Calendar]]
            [components.context.interface :as context]
            [store.pantry.events :as pantry-events]
            [store.pantry.subs :as pantry-subs]))

(def categories ["All" "Dairy" "Grains" "Produce" "Meat" "Pantry"])

(defui item-card [{:keys [name quantity category expires]}]
  ($ card/Card {:class "relative"}
     ($ card/CardHeader
        ($ :div {:class "flex items-start justify-between"}
           ($ :div
              ($ card/CardTitle {:class "text-lg"} name)
              ($ card/CardDescription {:class "flex items-center gap-2 mt-2"}
                 ($ Package {:size 14})
                 quantity))
           ($ badge/Badge {:variant "outline"} category)))
     ($ card/CardContent
        (when expires
          ($ :div {:class "flex items-center gap-2 text-sm text-muted-foreground"}
             ($ Calendar {:size 14})
             ($ :span "Expires: " expires))))))

(defui view []
  (let [ctx (context/use-context)
        api-client (:api/client ctx)

        ;; Subscribe to pantry items from store
        pantry-items (use-subscribe [::pantry-subs/pantry-items])
        loading (use-subscribe [::pantry-subs/loading])

        [selected-category set-selected-category] (use-state "All")
        [search set-search] (use-state "")
        [new-item-name set-new-item-name] (use-state "")

        ;; Fetch pantry items on mount
        _ (use-effect
            (fn []
              (when api-client
                (rf/dispatch [::pantry-events/fetch-pantry-items api-client]))
              (fn []))
            [api-client])

        filtered-items (cond->> pantry-items
                         (not= selected-category "All")
                         (filter #(= (:category %) selected-category))

                         (not-empty search)
                         (filter #(str/includes?
                                   (str/lower-case (:name %))
                                   (str/lower-case search))))]

    ($ :div {:class "space-y-6"}
       ;; Quick add section
       ($ card/Card
          ($ card/CardContent {:class "pt-6"}
             ($ :div {:class "flex gap-2"}
                ($ input/Input {:placeholder "Quick add item..."
                         :class "flex-1"
                         :value new-item-name
                         :on-change #(set-new-item-name (.. % -target -value))
                         :on-key-down #(when (= (.-key %) "Enter")
                                         (when (not-empty (str/trim new-item-name))
                                           (rf/dispatch [::pantry-events/add-pantry-item
                                                        {:name (str/trim new-item-name)
                                                         :quantity "1"
                                                         :category "Pantry"}
                                                        api-client
                                                        (fn [_]
                                                          (set-new-item-name "")
                                                          (rf/dispatch [::pantry-events/fetch-pantry-items api-client]))
                                                        nil])))})
                ($ button/Button
                   {:disabled (empty? (str/trim new-item-name))
                    :on-click #(when (not-empty (str/trim new-item-name))
                                 (rf/dispatch [::pantry-events/add-pantry-item
                                              {:name (str/trim new-item-name)
                                               :quantity "1"
                                               :category "Pantry"}
                                              api-client
                                              (fn [_]
                                                (set-new-item-name "")
                                                (rf/dispatch [::pantry-events/fetch-pantry-items api-client]))
                                              nil]))}
                   ($ Plus {:size 16 :class "mr-2"})
                   "Add Item"))))

       ;; Category filters
       ($ :div {:class "flex flex-wrap gap-2"}
          (for [cat categories]
            ($ button/Button {:key cat
                      :variant (if (= cat selected-category) "default" "outline")
                      :size "sm"
                      :on-click #(set-selected-category cat)}
               cat)))

       ;; Search
       ($ input/Input {:placeholder "Search pantry..."
                :value search
                :on-change #(set-search (.. % -target -value))})

       ;; Items grid
       (cond
         loading
         ($ card/Card
            ($ card/CardContent {:class "py-12 text-center"}
               ($ :p {:class "text-muted-foreground"} "Loading...")))

         (empty? filtered-items)
         ($ card/Card
            ($ card/CardContent {:class "py-12 text-center"}
               ($ :p {:class "text-muted-foreground"} "No items found")))

         :else
         ($ :div {:class "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
            (for [item filtered-items]
              ($ item-card {:key (:id item)
                           :name (:name item)
                           :quantity (:quantity item)
                           :category (:category item)
                           :expires (:expires item)})))))))
