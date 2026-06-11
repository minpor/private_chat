import { useCallback, useRef } from "react"
import {
  clearDraftRevision,
  getDraftRevision,
  waitForDraftStarted
} from "@/ws/draft-sync-coordinator"
import { wsClient } from "@/ws/ws-client"

const CLIENT_SESSION_ID = `web-${crypto.randomUUID().slice(0, 8)}`

export function useDraftSync(chatId: string | null) {
  const draftIdRef = useRef<string | null>(null)
  const patchTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const startingRef = useRef<Promise<void> | null>(null)

  const startDraft = useCallback(async (): Promise<string | null> => {
    if (!chatId) return null
    if (draftIdRef.current && startingRef.current) {
      await startingRef.current
      return draftIdRef.current
    }
    if (draftIdRef.current) {
      return draftIdRef.current
    }

    const draftId = crypto.randomUUID()
    draftIdRef.current = draftId

    const startPromise = (async () => {
      if (!wsClient.send("draft.start", {
        chatId,
        draftId,
        clientSessionId: CLIENT_SESSION_ID
      })) {
        throw new Error("WebSocket not connected")
      }
      await waitForDraftStarted(draftId)
    })()

    startingRef.current = startPromise
    try {
      await startPromise
      return draftId
    } catch (error) {
      draftIdRef.current = null
      throw error
    } finally {
      startingRef.current = null
    }
  }, [chatId])

  const patchText = useCallback(
    (text: string) => {
      if (!chatId) return

      if (patchTimerRef.current) {
        clearTimeout(patchTimerRef.current)
      }

      patchTimerRef.current = setTimeout(() => {
        void (async () => {
          try {
            const draftId = await startDraft()
            if (!draftId) return
            const revision = getDraftRevision(draftId) + 1
            wsClient.send("draft.patch", {
              chatId,
              draftId,
              revision,
              text
            })
          } catch {
            // ignore background patch errors
          }
        })()
      }, 300)
    },
    [chatId, startDraft]
  )

  const commitDraft = useCallback(
    async (text: string): Promise<{ clientMessageId: string } | null> => {
      if (!chatId) return null

      if (patchTimerRef.current) {
        clearTimeout(patchTimerRef.current)
        patchTimerRef.current = null
      }

      try {
        const draftId = await startDraft()
        if (!draftId) return null

        const revision = getDraftRevision(draftId) + 1
        if (!wsClient.send("draft.patch", {
          chatId,
          draftId,
          revision,
          text
        })) {
          return null
        }

        const clientMessageId = crypto.randomUUID()
        const sent = wsClient.send("draft.commit", {
          chatId,
          draftId,
          clientMessageId,
          expectedRevision: revision
        })

        if (!sent) {
          return null
        }

        clearDraftRevision(draftId)
        draftIdRef.current = null
        return { clientMessageId }
      } catch {
        return null
      }
    },
    [chatId, startDraft]
  )

  const resetDraft = useCallback(() => {
    if (patchTimerRef.current) {
      clearTimeout(patchTimerRef.current)
      patchTimerRef.current = null
    }
    if (chatId && draftIdRef.current) {
      wsClient.send("draft.discard", {
        chatId,
        draftId: draftIdRef.current
      })
      clearDraftRevision(draftIdRef.current)
    }
    draftIdRef.current = null
    startingRef.current = null
  }, [chatId])

  return { startDraft, patchText, commitDraft, resetDraft }
}
