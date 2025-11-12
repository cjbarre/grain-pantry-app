(ns components.ai.sidebar
  "AI Copilot sidebar component"
  (:require [uix.core :as uix :refer [defui $ use-state use-effect use-ref]]
            [re-frame.core :as rf]
            [re-frame.uix :refer [use-subscribe]]
            [clojure.string :as str]
            ["/gen/shadcn/components/ui/card" :as card]
            ["/gen/shadcn/components/ui/button" :as button]
            ["/gen/shadcn/components/ui/input" :as input]
            ["lucide-react" :refer [Send Bot User ChevronRight]]
            ["marked" :as marked]
            [store.ai.subs :as ai-subs]
            [store.ai.events :as ai-events]))

(defn- get-action-description
  "Extract description from action (handles both string and keyword keys)"
  [action]
  (get action "description" (:description action)))

(defn- build-action-summary
  "Build a compact summary of actions for display"
  [actions]
  (let [count (count actions)
        descriptions (map get-action-description actions)
        ;; Take first 2 descriptions, truncate if needed
        preview (take 2 descriptions)
        preview-str (str/join ", " preview)
        remaining (- count 2)]
    (if (> remaining 0)
      (str count " actions: " preview-str ", and " remaining " more...")
      (str count " action" (when (> count 1) "s") ": " preview-str))))

(defui suggested-actions-batch
  "Batch action execution component with compact summary and single confirm button"
  [{:keys [actions message-idx]}]
  (let [executing-batch (use-subscribe [::ai-subs/executing-batch])
        executed-batches (use-subscribe [::ai-subs/executed-batches])

        is-executing? (and executing-batch
                          (= (:message-idx executing-batch) message-idx))
        is-executed? (contains? executed-batches message-idx)

        current-idx (when is-executing? (:current-index executing-batch))
        total (when is-executing? (:total executing-batch))
        current-action (when (and is-executing? current-idx)
                        (get-in executing-batch [:actions current-idx]))

        summary (build-action-summary actions)

        handle-confirm (fn []
                        (when-not is-executed?
                          (rf/dispatch [::ai-events/execute-actions-batch actions message-idx])))]

    ($ :div {:class "mt-3 border border-border bg-muted/30 p-3"}
       ;; Summary or progress
       (if is-executing?
         ;; Show progress during execution
         ($ :div {:class "text-sm mb-2"}
            ($ :span {:class "text-muted-foreground"}
               (str "Executing " (inc current-idx) "/" total ": "))
            ($ :span {:class "font-medium"}
               (get-action-description current-action))
            ($ :span {:class "ml-2 animate-pulse"} "..."))

         ;; Show summary when idle
         ($ :div {:class "text-sm text-muted-foreground mb-2"}
            summary))

       ;; Confirm button
       ($ button/Button
          {:on-click handle-confirm
           :disabled (or is-executing? is-executed?)
           :variant (if is-executed? "outline" "default")
           :size "sm"
           :class "w-full"}
          (cond
            is-executing? "Executing..."
            is-executed? "✓ Completed"
            :else "Confirm All")))))

(defui chat-message
  "Individual chat message component"
  [{:keys [role content suggested-actions message-idx]}]
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

            ;; Suggested actions batch (only for assistant messages)
            (when (and (= role "assistant") (seq suggested-actions))
              ($ suggested-actions-batch
                 {:actions suggested-actions
                  :message-idx message-idx})))

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
      ;; Mobile: full width with small margins, Desktop (lg): fixed 384px width
      ($ card/Card {:class "fixed left-2 right-2 top-20 bottom-4 lg:left-auto lg:right-4 lg:w-96 flex flex-col z-50"}
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
                          :message-idx idx
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
