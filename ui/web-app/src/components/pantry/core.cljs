(ns components.pantry.core
  "Pantry Dashboard - Main entry point for pantry management.

   Backend Integration Ready:
   - Backend services: pantry-service and recipe-service are implemented
   - Frontend store: store.pantry.events, effects, and subs are ready
   - API endpoints: Commands and queries auto-generated from registries

   To complete integration:
   1. Add household creation/selection UI (or use user's default household)
   2. Wire components to re-frame store instead of mock data:
      - Use (rf/subscribe [::pantry-subs/pantry-items]) in pantry-list
      - Use (rf/dispatch [::pantry-events/fetch-pantry-items household-id api-client])
   3. Connect add/edit/delete actions to command dispatchers
   4. Handle loading states and errors from store

   Example integration:
   ```clojure
   (ns components.pantry.pantry-list
     (:require [re-frame.core :as rf]
               [store.pantry.events :as pantry-events]
               [store.pantry.subs :as pantry-subs]))

   (defui view []
     (let [items @(rf/subscribe [::pantry-subs/pantry-items])
           api-client (:api/client (context/use-context))
           household-id \"TODO: get from auth or context\"]
       (uix/use-effect
         (fn []
           (rf/dispatch [::pantry-events/fetch-pantry-items household-id api-client]))
         [household-id])
       ;; render with items from store...
       ))
   ```"
  (:require [uix.core :as uix :refer [defui $]]
            ["/gen/shadcn/components/ui/tabs" :as tabs]
            [components.pantry.pantry-list :as pantry-list]
            [components.pantry.recipe-suggestions :as recipes]
            [components.pantry.shopping-list :as shopping]))

(defui main []
  ($ :div {:class "container mx-auto p-6 max-w-7xl"}
     ;; Header
     ($ :div {:class "mb-6"}
        ($ :h1 {:class "text-3xl font-bold mb-2"} "Pantry Dashboard")
        ($ :p {:class "text-muted-foreground"}
           "Track your ingredients and discover recipes you can make"))

     ;; Tabs
     ($ tabs/Tabs {:defaultValue "pantry" :class "w-full"}
        ($ tabs/TabsList {:class "grid w-full grid-cols-3 mb-6"}
           ($ tabs/TabsTrigger {:value "pantry"} "My Pantry")
           ($ tabs/TabsTrigger {:value "recipes"} "Recipe Suggestions")
           ($ tabs/TabsTrigger {:value "shopping"} "Shopping List"))

        ;; Pantry Tab
        ($ tabs/TabsContent {:value "pantry"}
           ($ pantry-list/view))

        ;; Recipes Tab
        ($ tabs/TabsContent {:value "recipes"}
           ($ recipes/view))

        ;; Shopping List Tab
        ($ tabs/TabsContent {:value "shopping"}
           ($ shopping/view)))))
