import { Avatar, AvatarFallback } from "@/components/ui/avatar"
import type { ChatResponse } from "@/api/types"
import { chatTitle } from "@/lib/chat-peer"
import { cn, initials } from "@/lib/utils"
import { useChatStore } from "@/store/chat-store"

interface ChatListItemProps {
  chat: ChatResponse
  onSelect: (chatId: string) => void
}

export function ChatListItem({ chat, onSelect }: ChatListItemProps) {
  const activeChatId = useChatStore((s) => s.activeChatId)
  const peers = useChatStore((s) => s.peers)
  const isActive = activeChatId === chat.id
  const label = chatTitle(chat, peers)

  return (
    <button
      type="button"
      onClick={() => onSelect(chat.id)}
      className={cn(
        "flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-sidebar-accent",
        isActive && "bg-sidebar-accent"
      )}
    >
      <Avatar className="h-10 w-10">
        <AvatarFallback>{initials(label)}</AvatarFallback>
      </Avatar>
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium">{label}</p>
        <p className="truncate text-xs text-muted-foreground capitalize">{chat.type}</p>
      </div>
    </button>
  )
}
