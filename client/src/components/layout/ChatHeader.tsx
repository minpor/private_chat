import { ArrowLeft } from "lucide-react"
import { TypingIndicator } from "@/components/chat/TypingIndicator"
import type { ChatResponse } from "@/api/types"
import { Button } from "@/components/ui/button"
import { chatTitle, peerLabel } from "@/lib/chat-peer"
import { useAuthStore } from "@/store/auth-store"
import { useChatStore } from "@/store/chat-store"

interface ChatHeaderProps {
  chat: ChatResponse | undefined
}

export function ChatHeader({ chat }: ChatHeaderProps) {
  const sendMode = useChatStore((s) => s.setSendMode)
  const currentMode = useChatStore((s) => s.sendMode)
  const setSidebarOpen = useChatStore((s) => s.setSidebarOpen)
  const peers = useChatStore((s) => s.peers)
  const typingPeers = useChatStore((s) => s.typingPeers)
  const activeChatId = useChatStore((s) => s.activeChatId)
  const currentUserId = useAuthStore((s) => s.user?.id)

  const peerTyping =
    activeChatId && currentUserId
      ? (typingPeers[activeChatId] ?? []).filter((id) => id !== currentUserId)
      : []

  const title = chat ? chatTitle(chat, peers) : "Чат"
  const typingUserId = peerTyping[0]
  const typingPeer = activeChatId ? peers[activeChatId] ?? chat?.peer : null
  const typingLabel =
    typingUserId && typingPeer?.id === typingUserId
      ? `${peerLabel(typingPeer)} печатает`
      : typingUserId
        ? "Собеседник печатает"
        : null

  return (
    <div className="flex items-center justify-between gap-3 border-b border-border px-4 py-3">
      <div className="flex min-w-0 items-center gap-2">
        <Button
          variant="ghost"
          size="icon"
          className="md:hidden"
          onClick={() => setSidebarOpen(true)}
        >
          <ArrowLeft className="h-4 w-4" />
        </Button>
        <div className="min-w-0">
          <h2 className="truncate font-semibold">{title}</h2>
          {typingLabel ? (
            <TypingIndicator label={typingLabel} />
          ) : (
            <p className="text-xs text-muted-foreground capitalize">{chat?.type ?? "direct"}</p>
          )}
        </div>
      </div>

      <div className="flex shrink-0 items-center gap-1 rounded-lg bg-muted p-1">
        <Button
          size="sm"
          variant={currentMode === "draft" ? "default" : "ghost"}
          className="h-7 px-2 text-xs"
          onClick={() => sendMode("draft")}
        >
          Draft
        </Button>
        <Button
          size="sm"
          variant={currentMode === "rest" ? "default" : "ghost"}
          className="h-7 px-2 text-xs"
          onClick={() => sendMode("rest")}
        >
          REST
        </Button>
      </div>
    </div>
  )
}
