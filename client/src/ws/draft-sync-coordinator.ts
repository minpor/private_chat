import type { WsEnvelope } from "@/api/types"

interface DraftWaiter {
  draftId: string
  resolve: () => void
  reject: (error: Error) => void
  timer: ReturnType<typeof setTimeout>
}

const startWaiters: DraftWaiter[] = []
const revisions = new Map<string, number>()

export function getDraftRevision(draftId: string): number {
  return revisions.get(draftId) ?? 0
}

export function clearDraftRevision(draftId: string) {
  revisions.delete(draftId)
}

export function waitForDraftStarted(draftId: string, timeoutMs = 5000): Promise<void> {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      removeStartWaiter(draftId)
      reject(new Error("draft.start timeout"))
    }, timeoutMs)

    startWaiters.push({ draftId, resolve, reject, timer })
  })
}

function removeStartWaiter(draftId: string) {
  const index = startWaiters.findIndex((w) => w.draftId === draftId)
  if (index >= 0) {
    clearTimeout(startWaiters[index].timer)
    startWaiters.splice(index, 1)
  }
}

export function handleDraftSyncFrame(envelope: WsEnvelope): boolean {
  if (envelope.type === "draft.started" && envelope.payload) {
    const payload = envelope.payload as { draftId?: string; revision?: number }
    const draftId = payload.draftId
    if (!draftId) return true

    if (typeof payload.revision === "number") {
      revisions.set(draftId, payload.revision)
    }

    const waiter = startWaiters.find((w) => w.draftId === draftId)
    if (waiter) {
      clearTimeout(waiter.timer)
      waiter.resolve()
      removeStartWaiter(draftId)
    }
    return true
  }

  if (envelope.type === "draft.patch.ack" && envelope.payload) {
    const payload = envelope.payload as { draftId?: string; revision?: number }
    if (payload.draftId && typeof payload.revision === "number") {
      revisions.set(payload.draftId, payload.revision)
    }
    return true
  }

  if (envelope.type === "error" && envelope.payload) {
    const code = (envelope.payload as { code?: string }).code
    if (code === "DRAFT_GONE" || code === "DRAFT_REVISION_CONFLICT" || code === "BAD_REQUEST") {
      startWaiters.forEach((w) => {
        clearTimeout(w.timer)
        w.reject(new Error(code))
      })
      startWaiters.length = 0
    }
    return false
  }

  return false
}

export function rejectAllDraftWaiters(error: Error) {
  startWaiters.forEach((w) => {
    clearTimeout(w.timer)
    w.reject(error)
  })
  startWaiters.length = 0
}
