import { create } from "zustand"
import { persist } from "zustand/middleware"
import type { ChatPeerResponse, ChatResponse, SendMode, WsStatus } from "@/api/types"

interface ChatState {
  activeChatId: string | null
  sendMode: SendMode
  wsStatus: WsStatus
  peers: Record<string, ChatPeerResponse>
  typingPeers: Record<string, string[]>
  sidebarOpen: boolean
  setActiveChatId: (chatId: string | null) => void
  setSendMode: (mode: SendMode) => void
  setWsStatus: (status: WsStatus) => void
  setPeer: (chatId: string, peer: ChatPeerResponse) => void
  syncPeersFromChats: (chats: ChatResponse[]) => void
  setTyping: (chatId: string, userId: string, active: boolean) => void
  clearTyping: (chatId: string, userId?: string) => void
  setSidebarOpen: (open: boolean) => void
}

export const useChatStore = create<ChatState>()(
  persist(
    (set) => ({
      activeChatId: null,
      sendMode: "draft",
      wsStatus: "disconnected",
      peers: {},
      typingPeers: {},
      sidebarOpen: false,

      setActiveChatId: (chatId) => set({ activeChatId: chatId }),
      setSendMode: (mode) => set({ sendMode: mode }),
      setWsStatus: (status) => set({ wsStatus: status }),
      setPeer: (chatId, peer) =>
        set((state) => ({
          peers: { ...state.peers, [chatId]: peer }
        })),
      syncPeersFromChats: (chats) =>
        set((state) => {
          const peers = { ...state.peers }
          for (const chat of chats) {
            if (chat.peer) {
              peers[chat.id] = chat.peer
            }
          }
          return { peers }
        }),
      setTyping: (chatId, userId, active) =>
        set((state) => {
          const current = new Set(state.typingPeers[chatId] ?? [])
          if (active) {
            current.add(userId)
          } else {
            current.delete(userId)
          }
          return {
            typingPeers: {
              ...state.typingPeers,
              [chatId]: [...current]
            }
          }
        }),
      clearTyping: (chatId, userId) =>
        set((state) => {
          if (!userId) {
            const { [chatId]: _, ...rest } = state.typingPeers
            return { typingPeers: rest }
          }
          const current = (state.typingPeers[chatId] ?? []).filter((id) => id !== userId)
          return {
            typingPeers: {
              ...state.typingPeers,
              [chatId]: current
            }
          }
        }),
      setSidebarOpen: (open) => set({ sidebarOpen: open })
    }),
    {
      name: "private-chat-ui",
      partialize: (state) => ({
        sendMode: state.sendMode
      })
    }
  )
)
