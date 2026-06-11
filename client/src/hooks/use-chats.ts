import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import * as chatsApi from "@/api/chats"
import { useChatStore } from "@/store/chat-store"

export function useChats() {
  const syncPeersFromChats = useChatStore((s) => s.syncPeersFromChats)

  return useQuery({
    queryKey: ["chats"],
    queryFn: async () => {
      const chats = await chatsApi.listChats()
      syncPeersFromChats(chats)
      return chats
    }
  })
}

export function useCreateChat() {
  const queryClient = useQueryClient()
  const setPeer = useChatStore((s) => s.setPeer)
  const setActiveChatId = useChatStore((s) => s.setActiveChatId)

  return useMutation({
    mutationFn: (username: string) => chatsApi.createDirectChat(username),
    onSuccess: (chat) => {
      if (chat.peer) {
        setPeer(chat.id, chat.peer)
      }
      setActiveChatId(chat.id)
      queryClient.invalidateQueries({ queryKey: ["chats"] })
    }
  })
}
