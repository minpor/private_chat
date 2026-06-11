import { api } from "./client"
import type { ChatResponse } from "./types"

export function listChats(limit = 50): Promise<ChatResponse[]> {
  return api.get<ChatResponse[]>(`/chats?limit=${limit}`)
}

export function createDirectChat(username: string): Promise<ChatResponse> {
  return api.post<ChatResponse>("/chats", { username })
}

export function getChat(chatId: string): Promise<ChatResponse> {
  return api.get<ChatResponse>(`/chats/${chatId}`)
}
