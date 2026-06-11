import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import * as messagesApi from "@/api/messages"
import type { MessageListResponse, MessageResponse } from "@/api/types"
import {
  appendOptimisticMessage,
  restoreMessagesCache
} from "@/lib/message-cache"
import { useAuthStore } from "@/store/auth-store"

export function useMessages(chatId: string | null) {
  return useQuery({
    queryKey: ["messages", chatId],
    queryFn: () => messagesApi.listMessages(chatId!),
    enabled: !!chatId,
    select: (data) => data.messages
  })
}

export function useSendMessage(chatId: string | null) {
  const queryClient = useQueryClient()
  const userId = useAuthStore((s) => s.user?.id)

  return useMutation({
    mutationFn: ({ clientMessageId, text }: { clientMessageId: string; text: string }) =>
      messagesApi.sendMessage(chatId!, clientMessageId, text),
    onMutate: async ({ clientMessageId, text }) => {
      if (!chatId) return

      await queryClient.cancelQueries({ queryKey: ["messages", chatId] })
      const previous = queryClient.getQueryData<MessageListResponse>(["messages", chatId])

      const optimistic: MessageResponse = {
        id: `optimistic-${clientMessageId}`,
        chatId,
        senderId: userId ?? "",
        clientMessageId,
        text,
        replyTo: null,
        createdAt: new Date().toISOString()
      }

      appendOptimisticMessage(queryClient, chatId, optimistic)

      return { previous }
    },
    onError: (_err, _vars, context) => {
      if (chatId && context?.previous) {
        restoreMessagesCache(queryClient, chatId, context.previous)
      }
    },
    onSettled: () => {
      if (chatId) {
        queryClient.invalidateQueries({ queryKey: ["messages", chatId] })
      }
    }
  })
}
