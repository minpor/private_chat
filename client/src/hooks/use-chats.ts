import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import * as chatsApi from "@/api/chats"
import { useChatStore } from "@/store/chat-store"

export function useChats() {
  return useQuery({
    queryKey: ["chats"],
    queryFn: () => chatsApi.listChats()
  })
}

export function useCreateChat() {
  const queryClient = useQueryClient()
  const setPeerName = useChatStore((s) => s.setPeerName)
  const setActiveChatId = useChatStore((s) => s.setActiveChatId)

  return useMutation({
    mutationFn: (username: string) => chatsApi.createDirectChat(username),
    onSuccess: (chat, username) => {
      setPeerName(chat.id, username)
      setActiveChatId(chat.id)
      queryClient.invalidateQueries({ queryKey: ["chats"] })
    }
  })
}
