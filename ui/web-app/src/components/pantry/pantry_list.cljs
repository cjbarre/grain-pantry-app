(ns components.pantry.pantry-list
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-ref]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/badge" :as badge]
            ["/gen/shadcn/components/ui/input" :as input]
            ["/gen/shadcn/components/ui/label" :as label]
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

        ;; Form state from Re-frame store
        new-item-name (use-subscribe [::pantry-subs/form-name])
        new-item-quantity (use-subscribe [::pantry-subs/form-quantity])
        new-item-category (use-subscribe [::pantry-subs/form-category])
        new-item-expires (use-subscribe [::pantry-subs/form-expires])
        form-expanded (use-subscribe [::pantry-subs/form-expanded])
        category-error (use-subscribe [::pantry-subs/form-error])
        form-valid? (use-subscribe [::pantry-subs/form-valid?])

        ;; Ref for click-outside detection
        card-ref (use-ref nil)

        ;; Helper to calculate expiration dates
        add-days (fn [days]
                   (let [date (js/Date.)
                         _ (.setDate date (+ (.getDate date) days))
                         year (.getFullYear date)
                         month (-> (.getMonth date) inc (str) (.padStart 2 "0"))
                         day (-> (.getDate date) (str) (.padStart 2 "0"))]
                     (str year "-" month "-" day)))

        ;; Fetch pantry items on mount
        _ (use-effect
            (fn []
              (when api-client
                (rf/dispatch [::pantry-events/fetch-pantry-items api-client]))
              (fn []))
            [api-client])

        ;; Click outside to collapse form
        _ (use-effect
            (fn []
              (let [handle-click-outside
                    (fn [event]
                      (let [target (.-target event)
                            ;; Check if click is on a portal element (Select dropdown, etc.)
                            in-portal? (.closest target "[role='listbox'], [role='option'], [data-radix-popper-content-wrapper]")]
                        (when (and form-expanded
                                   @card-ref
                                   (not (.contains @card-ref target))
                                   (not in-portal?))
                          (rf/dispatch [::pantry-events/set-form-expanded false]))))]
                (.addEventListener js/document "mousedown" handle-click-outside)
                (fn []
                  (.removeEventListener js/document "mousedown" handle-click-outside))))
            [form-expanded])

        filtered-items (cond->> pantry-items
                         (not= selected-category "All")
                         (filter #(= (:category %) selected-category))

                         (not-empty search)
                         (filter #(str/includes?
                                   (str/lower-case (:name %))
                                   (str/lower-case search))))

        handle-submit (fn []
                        (rf/dispatch [::pantry-events/set-form-error false])
                        (when form-valid?
                          (rf/dispatch [::pantry-events/add-pantry-item
                                       {:name (str/trim new-item-name)
                                        :quantity (str/trim new-item-quantity)
                                        :category new-item-category
                                        :expires (when (not-empty (str/trim new-item-expires))
                                                  (str/trim new-item-expires))}
                                       api-client
                                       (fn [_]
                                         (rf/dispatch [::pantry-events/reset-form])
                                         (rf/dispatch [::pantry-events/fetch-pantry-items api-client]))
                                       nil]))
                        (when-not form-valid?
                          (rf/dispatch [::pantry-events/set-form-error true])))]

    ($ :div {:class "space-y-6"}
         ;; Quick add section with expandable form
         ($ card/Card {:ref card-ref}
            ($ card/CardContent {:class "pt-6"}
               ($ :div {:class "space-y-4"}
                  ;; Name input - always visible
                  ($ :div {:class "flex gap-2"}
                     ($ input/Input {:placeholder "Quick add item..."
                                     :class "flex-1"
                                     :value new-item-name
                                     :on-change #(rf/dispatch [::pantry-events/set-form-field :name (.. % -target -value)])
                                     :on-focus #(rf/dispatch [::pantry-events/set-form-expanded true])
                                     :on-key-down #(when (= (.-key %) "Enter")
                                                    (handle-submit))})
                     ($ button/Button
                        {:disabled (not form-valid?)
                         :on-click handle-submit}
                        ($ Plus {:size 16 :class "mr-2"})
                        "Add Item"))

                  ;; Expanded fields - only show when form is expanded
                  (when form-expanded
                    ($ :div {:class "space-y-4 pt-2"}
                       ;; Quantity input
                       ($ :div {:class "space-y-2"}
                          ($ label/Label {:for "quantity"} "Quantity")
                          ($ input/Input {:id "quantity"
                                         :placeholder "e.g., 1, 2 cups, 500g"
                                         :value new-item-quantity
                                         :on-change #(rf/dispatch [::pantry-events/set-form-field :quantity (.. % -target -value)])}))

                       ;; Category badges
                       ($ :div {:class "space-y-2"}
                          ($ label/Label "Category")
                          ($ :div {:class "flex flex-wrap gap-2"}
                             (for [cat (filter #(not= % "All") categories)]
                               ($ badge/Badge
                                  {:key cat
                                   :variant (if (= cat new-item-category) "default" "outline")
                                   :class "cursor-pointer hover:bg-accent"
                                   :on-click #(rf/dispatch [::pantry-events/set-form-field :category cat])}
                                  cat)))
                          (when category-error
                            ($ :p {:class "text-sm text-red-500"}
                               "Please select a category")))

                       ;; Expiration quick options
                       ($ :div {:class "space-y-2"}
                          ($ label/Label "Expires")
                          ($ :div {:class "flex flex-wrap gap-2"}
                             ($ button/Button
                                {:variant "outline"
                                 :size "sm"
                                 :type "button"
                                 :on-click #(rf/dispatch [::pantry-events/set-form-field :expires (add-days 1)])}
                                "1 day")
                             ($ button/Button
                                {:variant "outline"
                                 :size "sm"
                                 :type "button"
                                 :on-click #(rf/dispatch [::pantry-events/set-form-field :expires (add-days 3)])}
                                "3 days")
                             ($ button/Button
                                {:variant "outline"
                                 :size "sm"
                                 :type "button"
                                 :on-click #(rf/dispatch [::pantry-events/set-form-field :expires (add-days 7)])}
                                "1 week")
                             ($ button/Button
                                {:variant "outline"
                                 :size "sm"
                                 :type "button"
                                 :on-click #(rf/dispatch [::pantry-events/set-form-field :expires (add-days 14)])}
                                "2 weeks")
                             ($ button/Button
                                {:variant "outline"
                                 :size "sm"
                                 :type "button"
                                 :on-click #(rf/dispatch [::pantry-events/set-form-field :expires ""])}
                                "None"))
                          (when (not-empty new-item-expires)
                            ($ :p {:class "text-sm text-muted-foreground"}
                               "Expires: " new-item-expires))))))))

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
