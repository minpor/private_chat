import { create } from "zustand"
import { persist } from "zustand/middleware"
import type { SendMode, WsStatus } from "@/api/types"

interface ChatState {
  activeChatId: string | null
  sendMode: SendMode
  wsStatus: WsStatus
  peerNames: Record<string, string>
  typingPeers: Record<string, string[]>
  sidebarOpen: boolean
  setActiveChatId: (chatId: string | null) => void
  setSendMode: (mode: SendMode) => void
  setWsStatus: (status: WsStatus) => void
  setPeerName: (chatId: string, name: string) => void
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
      peerNames: {},
      typingPeers: {},
      sidebarOpen: false,

      setActiveChatId: (chatId) => set({ activeChatId: chatId }),
      setSendMode: (mode) => set({ sendMode: mode }),
      setWsStatus: (status) => set({ wsStatus: status }),
      setPeerName: (chatId, name) =>
        set((state) => ({
          peerNames: { ...state.peerNames, [chatId]: name }
        })),
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
        sendMode: state.sendMode,
        peerNames: state.peerNames
      })
    }
  )
)
