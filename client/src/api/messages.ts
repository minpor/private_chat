import { api } from "./client"
import type { MessageAcceptedResponse, MessageListResponse } from "./types"

export function listMessages(
  chatId: string,
  limit = 50,
  before?: string
): Promise<MessageListResponse> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (before) {
    params.set("before", before)
  }
  return api.get<MessageListResponse>(`/chats/${chatId}/messages?${params}`)
}

export function sendMessage(
  chatId: string,
  clientMessageId: string,
  text: string
): Promise<MessageAcceptedResponse> {
  return api.post<MessageAcceptedResponse>(`/chats/${chatId}/messages`, {
    clientMessageId,
    text
  })
}
