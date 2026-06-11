import type { QueryClient } from "@tanstack/react-query"
import type { MessageListResponse, MessageResponse } from "@/api/types"

export function getMessagesFromCache(
  queryClient: QueryClient,
  chatId: string
): MessageResponse[] {
  return queryClient.getQueryData<MessageListResponse>(["messages", chatId])?.messages ?? []
}

export function appendMessageToCache(
  queryClient: QueryClient,
  chatId: string,
  message: MessageResponse
) {
  queryClient.setQueryData<MessageListResponse>(["messages", chatId], (old) => {
    const messages = old?.messages ?? []
    const withoutOptimistic = messages.filter(
      (m) =>
        !m.id.startsWith("optimistic-") ||
        m.clientMessageId !== message.clientMessageId
    )
    if (withoutOptimistic.some((m) => m.id === message.id)) {
      return old ?? { messages: withoutOptimistic, nextBefore: null }
    }
    return {
      messages: [...withoutOptimistic, message],
      nextBefore: old?.nextBefore ?? null
    }
  })
}

export function appendOptimisticMessage(
  queryClient: QueryClient,
  chatId: string,
  message: MessageResponse
) {
  queryClient.setQueryData<MessageListResponse>(["messages", chatId], (old) => ({
    messages: [...(old?.messages ?? []), message],
    nextBefore: old?.nextBefore ?? null
  }))
}

export function restoreMessagesCache(
  queryClient: QueryClient,
  chatId: string,
  data: MessageListResponse | undefined
) {
  queryClient.setQueryData<MessageListResponse>(["messages", chatId], data)
}
