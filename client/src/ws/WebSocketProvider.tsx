import { useQueryClient } from "@tanstack/react-query"
import { useEffect, useRef } from "react"
import { toast } from "sonner"
import type {
  MessageNewPayload,
  MessageResponse,
  TypingIndicatorPayload,
  WsEnvelope
} from "@/api/types"
import { useTokenRefresh } from "@/hooks/use-token-refresh"
import { appendMessageToCache } from "@/lib/message-cache"
import { useAuthStore } from "@/store/auth-store"
import { useChatStore } from "@/store/chat-store"
import { handleDraftSyncFrame } from "./draft-sync-coordinator"
import { wsClient } from "./ws-client"

const TYPING_TTL_MS = 3000

function toMessage(payload: MessageNewPayload): MessageResponse {
  return {
    id: payload.id,
    chatId: payload.chatId,
    senderId: payload.senderId,
    clientMessageId: payload.id,
    text: payload.text,
    replyTo: null,
    createdAt: payload.createdAt
  }
}

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const userId = useAuthStore((s) => s.user?.id)
  const setWsStatus = useChatStore((s) => s.setWsStatus)
  const activeChatIdRef = useRef(useChatStore.getState().activeChatId)
  const typingTimersRef = useRef<Map<string, ReturnType<typeof setTimeout>>>(new Map())
  const queryClient = useQueryClient()

  useTokenRefresh()

  useEffect(() => {
    return useChatStore.subscribe((state) => {
      activeChatIdRef.current = state.activeChatId
    })
  }, [])

  useEffect(() => {
    wsClient.setStatusHandler(setWsStatus)
    wsClient.configure({
      getToken: () => useAuthStore.getState().accessToken,
      refreshToken: () => useAuthStore.getState().refreshTokens()
    })
  }, [setWsStatus])

  useEffect(() => {
    if (!accessToken) {
      wsClient.disconnect()
      return
    }

    const clearTypingTimer = (chatId: string, peerId: string) => {
      const key = `${chatId}:${peerId}`
      const timer = typingTimersRef.current.get(key)
      if (timer) {
        clearTimeout(timer)
        typingTimersRef.current.delete(key)
      }
    }

    const scheduleTypingExpiry = (chatId: string, peerId: string) => {
      const key = `${chatId}:${peerId}`
      clearTypingTimer(chatId, peerId)
      typingTimersRef.current.set(
        key,
        setTimeout(() => {
          useChatStore.getState().setTyping(chatId, peerId, false)
          typingTimersRef.current.delete(key)
        }, TYPING_TTL_MS)
      )
    }

    const handleMessage = (envelope: WsEnvelope) => {
      handleDraftSyncFrame(envelope)

      if (envelope.type === "typing.indicator" && envelope.payload) {
        const payload = envelope.payload as unknown as TypingIndicatorPayload
        if (payload.userId === userId) return

        if (payload.active) {
          useChatStore.getState().setTyping(payload.chatId, payload.userId, true)
          scheduleTypingExpiry(payload.chatId, payload.userId)
        } else {
          clearTypingTimer(payload.chatId, payload.userId)
          useChatStore.getState().setTyping(payload.chatId, payload.userId, false)
        }
        return
      }

      if (envelope.type === "message.new" && envelope.payload) {
        const payload = envelope.payload as unknown as MessageNewPayload
        const message = toMessage(payload)

        clearTypingTimer(payload.chatId, payload.senderId)
        useChatStore.getState().setTyping(payload.chatId, payload.senderId, false)
        appendMessageToCache(queryClient, payload.chatId, message)

        if (payload.chatId !== activeChatIdRef.current && payload.senderId !== userId) {
          toast.message("Новое сообщение", { description: payload.text.slice(0, 80) })
        }
        return
      }

      if (envelope.type === "message.accepted" && envelope.payload) {
        const payload = envelope.payload as { chatId?: string }
        if (payload.chatId) {
          queryClient.invalidateQueries({ queryKey: ["messages", payload.chatId] })
        }
        return
      }

      if (envelope.type === "error" && envelope.payload) {
        const err = envelope.payload as { code?: string; message?: string }
        toast.error(err.message ?? "WebSocket error", {
          description: err.code
        })
      }
    }

    wsClient.setMessageHandler(handleMessage)
    wsClient.disconnect()
    wsClient.connect()

    return () => {
      typingTimersRef.current.forEach((timer) => clearTimeout(timer))
      typingTimersRef.current.clear()
      wsClient.disconnect()
    }
  }, [accessToken, queryClient, userId])

  return children
}
