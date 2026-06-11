import { useQueryClient } from "@tanstack/react-query"
import { Send } from "lucide-react"
import { useEffect, useRef, useState } from "react"
import { toast } from "sonner"
import { Button } from "@/components/ui/button"
import { Textarea } from "@/components/ui/textarea"
import { useDraftSync } from "@/hooks/use-draft-sync"
import { useSendMessage } from "@/hooks/use-messages"
import { appendOptimisticMessage } from "@/lib/message-cache"
import { useAuthStore } from "@/store/auth-store"
import { useChatStore } from "@/store/chat-store"

interface MessageComposerProps {
  chatId: string
}

export function MessageComposer({ chatId }: MessageComposerProps) {
  const [text, setText] = useState("")
  const sendMode = useChatStore((s) => s.sendMode)
  const sendMessage = useSendMessage(chatId)
  const userId = useAuthStore((s) => s.user?.id)
  const queryClient = useQueryClient()
  const { startDraft, patchText, commitDraft, resetDraft } = useDraftSync(chatId)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  useEffect(() => {
    setText("")
    resetDraft()
  }, [chatId, resetDraft])

  function handleFocus() {
    if (sendMode === "draft") {
      void startDraft().catch(() => {
        toast.error("Не удалось начать черновик")
      })
    }
  }

  function handleChange(value: string) {
    setText(value)
    if (sendMode === "draft" && value.trim()) {
      patchText(value)
    }
  }

  async function handleSend() {
    const trimmed = text.trim()
    if (!trimmed) return

    if (sendMode === "rest") {
      const clientMessageId = crypto.randomUUID()
      try {
        await sendMessage.mutateAsync({ clientMessageId, text: trimmed })
        setText("")
      } catch {
        toast.error("Не удалось отправить сообщение")
      }
      return
    }

    const result = await commitDraft(trimmed)
    if (result && userId) {
      appendOptimisticMessage(queryClient, chatId, {
        id: `optimistic-${result.clientMessageId}`,
        chatId,
        senderId: userId,
        clientMessageId: result.clientMessageId,
        text: trimmed,
        replyTo: null,
        createdAt: new Date().toISOString()
      })
      setText("")
    } else {
      toast.error("Не удалось отправить через draft-sync")
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault()
      handleSend()
    }
  }

  return (
    <div className="border-t border-border bg-card/50 p-4 backdrop-blur-sm">
      {sendMode === "draft" && (
        <p className="mb-2 text-xs text-muted-foreground">Draft-sync: текст синхронизируется через WebSocket</p>
      )}
      <div className="flex items-end gap-2">
        <Textarea
          ref={textareaRef}
          value={text}
          onChange={(e) => handleChange(e.target.value)}
          onFocus={handleFocus}
          onKeyDown={handleKeyDown}
          placeholder="Написать сообщение..."
          rows={1}
          className="min-h-[44px] max-h-32 resize-none"
        />
        <Button
          size="icon"
          className="h-11 w-11 shrink-0 rounded-full"
          onClick={handleSend}
          disabled={!text.trim() || sendMessage.isPending}
        >
          <Send className="h-4 w-4" />
        </Button>
      </div>
    </div>
  )
}
