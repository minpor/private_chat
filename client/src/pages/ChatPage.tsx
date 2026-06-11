import { useQuery } from "@tanstack/react-query"
import { ChatHeader } from "@/components/layout/ChatHeader"
import { MessageComposer } from "@/components/chat/MessageComposer"
import { MessageList } from "@/components/chat/MessageList"
import { EmptyState } from "@/components/common/EmptyState"
import { AppShell } from "@/components/layout/AppShell"
import * as chatsApi from "@/api/chats"
import { useMessages } from "@/hooks/use-messages"
import { useChatStore } from "@/store/chat-store"

export function ChatPage() {
  const activeChatId = useChatStore((s) => s.activeChatId)
  const setPeer = useChatStore((s) => s.setPeer)

  const { data: chat } = useQuery({
    queryKey: ["chat", activeChatId],
    queryFn: async () => {
      const loaded = await chatsApi.getChat(activeChatId!)
      if (loaded.peer) {
        setPeer(loaded.id, loaded.peer)
      }
      return loaded
    },
    enabled: !!activeChatId
  })

  const { data: messages, isLoading } = useMessages(activeChatId)

  return (
    <AppShell>
      {activeChatId ? (
        <>
          <ChatHeader chat={chat} />
          <MessageList messages={messages} isLoading={isLoading} />
          <MessageComposer chatId={activeChatId} />
        </>
      ) : (
        <EmptyState
          title="Выберите чат"
          description="Создайте новый direct-чат или выберите существующий из списка слева"
        />
      )}
    </AppShell>
  )
}
