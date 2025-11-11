(ns components.ai.sidebar
  "AI Copilot sidebar component"
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-ref]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["lucide-react" :refer [Send Bot User ChevronRight ChevronDown]]
            [store.ai.subs :as ai-subs]
            [store.ai.events :as ai-events]))

(defui chat-message
  "Individual chat message component"
  [{:keys [role content]}]
  ($ :div {:class (str "flex gap-3 mb-3 "
                      (if (= role "user") "justify-end" "justify-start"))}
     ;; Avatar icon
     (when (= role "assistant")
       ($ :div {:class "w-8 h-8 flex items-center justify-center border-2 border-primary bg-card"}
          ($ Bot {:size 20 :class "text-primary"})))

     ;; Message bubble
     ($ :div {:class (str "border-2 px-3 py-2 max-w-[75%] "
                         (if (= role "user")
                           "bg-primary text-primary-foreground border-primary"
                           "bg-card border-border"))}
        content)

     ;; User avatar
     (when (= role "user")
       ($ :div {:class "w-8 h-8 flex items-center justify-center border-2 border-foreground bg-card"}
          ($ User {:size 20})))))

(defui ai-sidebar
  "AI Copilot sidebar - collapsible chat interface"
  []
  (let [conversation (use-subscribe [::ai-subs/conversation])
        loading (use-subscribe [::ai-subs/loading])
        [input-value set-input-value] (use-state "")
        [collapsed set-collapsed] (use-state false)
        messages-end-ref (use-ref nil)

        handle-send (fn []
                     (when (and (not-empty input-value) (not loading))
                       (rf/dispatch [::ai-events/ask-ai input-value])
                       (set-input-value "")))

        ;; Auto-scroll to bottom on new messages
        _ (use-effect
            (fn []
              (when @messages-end-ref
                (.scrollIntoView @messages-end-ref #js {:behavior "smooth"}))
              js/undefined)
            [(count conversation)])]

    (if collapsed
      ;; Collapsed state - floating button
      ($ button/Button
         {:class "fixed right-4 bottom-4 w-14 h-14"
          :size "icon"
          :on-click #(set-collapsed false)}
         ($ Bot {:size 24}))

      ;; Expanded state - full sidebar
      ($ card/Card {:class "fixed right-4 top-20 bottom-4 w-96 flex flex-col"}
         ($ card/CardHeader {:class "flex-row items-center justify-between pb-3 cursor-pointer"
                            :on-click #(set-collapsed true)}
            ($ :div {:class "flex items-center gap-2"}
               ($ Bot {:size 20 :class "text-primary"})
               ($ card/CardTitle "AI Copilot"))
            ($ ChevronRight {:size 20 :class "text-muted-foreground"}))

         ($ card/CardContent {:class "flex-1 flex flex-col min-h-0 pb-3"}
            ;; Chat history
            ($ :div {:class "flex-1 overflow-y-auto mb-3"}
               (if (empty? conversation)
                 ($ :div {:class "text-center text-muted-foreground mt-8"}
                    ($ :p "👋 Hi! Ask me anything about your pantry.")
                    ($ :p {:class "text-sm mt-2"}
                       "Try: \"What can I make for dinner?\""))

                 ($ :<>
                    (for [[idx msg] (map-indexed vector conversation)]
                      ($ chat-message
                         {:key idx
                          :role (:role msg)
                          :content (:content msg)}))
                    ($ :div {:ref messages-end-ref})))

               (when loading
                 ($ :div {:class "text-center text-muted-foreground animate-pulse"}
                    "🤖 Thinking...")))

            ;; Input
            ($ :div {:class "flex gap-2"}
               ($ input/Input
                  {:value input-value
                   :disabled loading
                   :on-change #(set-input-value (.. % -target -value))
                   :on-key-down #(when (= (.-key %) "Enter") (handle-send))
                   :placeholder "Ask me anything..."})
               ($ button/Button
                  {:on-click handle-send
                   :disabled (or loading (empty? input-value))}
                  ($ Send {:size 16}))))))))
