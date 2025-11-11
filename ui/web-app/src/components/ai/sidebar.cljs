(ns components.ai.sidebar
  "AI Copilot sidebar component"
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-ref]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["lucide-react" :refer [Send Bot User ChevronRight ChevronDown]]
            ["marked" :as marked]
            [store.ai.subs :as ai-subs]
            [store.ai.events :as ai-events]))

(defui suggested-action-button
  "Button for executing AI-suggested actions"
  [{:keys [action executing? executed?]}]
  ($ button/Button
     {:on-click #(rf/dispatch [::ai-events/execute-suggested-action action])
      :disabled (or executing? executed?)
      :variant "outline"
      :size "sm"
      :class "mt-2 w-full justify-start text-left h-auto min-h-[2rem] whitespace-normal"}
     ($ :div {:class "flex items-start gap-2 w-full py-1"}
        (when executed?
          ($ :span {:class "shrink-0 mt-0.5"} "✓ "))
        ($ :span {:class "flex-1 break-words"}
           (get action "description" (:description action)))
        (when executing?
          ($ :span {:class "shrink-0 animate-pulse"} "...")))))

(defui chat-message
  "Individual chat message component"
  [{:keys [role content suggested-actions]}]
  (let [rendered-html (when (= role "assistant")
                        (marked/parse content #js {:breaks true :gfm true}))]
    (cond
      ;; System messages (action feedback) - centered, no avatar
      (= role "system")
      ($ :div {:class "flex justify-center mb-3"}
         ($ :div {:class "text-sm text-muted-foreground px-3 py-1 border border-border bg-muted max-w-[85%]"}
            content))

      ;; Regular user/assistant messages
      :else
      ($ :div {:class (str "flex gap-3 mb-3 "
                          (if (= role "user") "justify-end" "justify-start"))}
         ;; Avatar icon
         (when (= role "assistant")
           ($ :div {:class "w-8 h-8 flex items-center justify-center border-2 border-primary bg-card"}
              ($ Bot {:size 20 :class "text-primary"})))

         ;; Message bubble with actions
         ($ :div {:class (str "border-2 px-3 py-2 max-w-[75%] "
                             (if (= role "user")
                               "bg-primary text-primary-foreground border-primary"
                               "bg-card border-border"))}
            ;; Render markdown for assistant, plain text for user
            (if (= role "assistant")
              ($ :div {:class "prose prose-sm max-w-none"
                       :dangerouslySetInnerHTML #js {:__html rendered-html}})
              ($ :div content))

            ;; Suggested actions (only for assistant messages)
            (when (and (= role "assistant") (seq suggested-actions))
              ($ :div {:class "mt-3 flex flex-col gap-2"}
                 (for [[idx action] (map-indexed vector suggested-actions)]
                   ($ suggested-action-button
                      {:key idx
                       :action action
                       :executing? false
                       :executed? false})))))

         ;; User avatar
         (when (= role "user")
           ($ :div {:class "w-8 h-8 flex items-center justify-center border-2 border-foreground bg-card"}
              ($ User {:size 20})))))))

(defui ai-sidebar
  "AI Copilot sidebar - collapsible chat interface"
  [{:keys [collapsed on-toggle]}]
  (let [conversation (use-subscribe [::ai-subs/conversation])
        loading (use-subscribe [::ai-subs/loading])
        [input-value set-input-value] (use-state "")
        messages-end-ref (use-ref nil)

        handle-send (fn []
                     (when (and (not-empty input-value) (not loading))
                       (rf/dispatch [::ai-events/ask-ai input-value])
                       (set-input-value "")))

        handle-toggle (fn []
                       (when on-toggle (on-toggle)))

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
         {:class "fixed right-4 bottom-4 w-14 h-14 z-50"
          :size "icon"
          :on-click handle-toggle}
         ($ Bot {:size 24}))

      ;; Expanded state - full sidebar
      ($ card/Card {:class "fixed right-4 top-20 bottom-4 w-96 flex flex-col z-50"}
         ($ card/CardHeader {:class "flex-row items-center justify-between pb-3 cursor-pointer"
                            :on-click handle-toggle}
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
                          :content (:content msg)
                          :suggested-actions (:suggested-actions msg)}))
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
