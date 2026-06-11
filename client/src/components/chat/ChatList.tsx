import { RefreshCw } from "lucide-react"
import { ChatListItem } from "@/components/chat/ChatListItem"
import { NewChatDialog } from "@/components/chat/NewChatDialog"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import { useChats } from "@/hooks/use-chats"
import { useChatStore } from "@/store/chat-store"
import { useMemo, useState } from "react"

export function ChatList() {
  const { data: chats, isLoading, refetch, isFetching } = useChats()
  const setActiveChatId = useChatStore((s) => s.setActiveChatId)
  const setSidebarOpen = useChatStore((s) => s.setSidebarOpen)
  const [search, setSearch] = useState("")

  const filtered = useMemo(() => {
    const list = chats ?? []
    const q = search.trim().toLowerCase()
    if (!q) return list
    return list.filter((chat) => {
      const peer = useChatStore.getState().peerNames[chat.id] ?? chat.title ?? chat.id
      return peer.toLowerCase().includes(q) || chat.type.includes(q)
    })
  }, [chats, search])

  function selectChat(chatId: string) {
    setActiveChatId(chatId)
    setSidebarOpen(false)
  }

  return (
    <div className="flex h-full flex-col">
      <div className="space-y-3 border-b border-sidebar-border p-4">
        <div className="flex items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">Чаты</h2>
          <div className="flex gap-1">
            <Button
              variant="ghost"
              size="icon"
              onClick={() => refetch()}
              disabled={isFetching}
              aria-label="Обновить"
            >
              <RefreshCw className={`h-4 w-4 ${isFetching ? "animate-spin" : ""}`} />
            </Button>
            <NewChatDialog />
          </div>
        </div>
        <Input
          placeholder="Поиск..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
      </div>

      <ScrollArea className="flex-1 p-2">
        {isLoading ? (
          <div className="space-y-2 p-2">
            {Array.from({ length: 5 }).map((_, i) => (
              <Skeleton key={i} className="h-14 w-full rounded-lg" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <p className="p-4 text-center text-sm text-muted-foreground">
            {search ? "Ничего не найдено" : "Нет чатов. Создайте новый."}
          </p>
        ) : (
          <div className="space-y-0.5">
            {filtered.map((chat) => (
              <ChatListItem key={chat.id} chat={chat} onSelect={selectChat} />
            ))}
          </div>
        )}
      </ScrollArea>
    </div>
  )
}
