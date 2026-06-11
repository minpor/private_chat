import type { WsEnvelope } from "@/api/types"

export type WsMessageHandler = (envelope: WsEnvelope) => void
type TokenProvider = () => string | null
type TokenRefresher = () => Promise<string | null>

export class WsClient {
  private socket: WebSocket | null = null
  private handler: WsMessageHandler | null = null
  private tokenProvider: TokenProvider = () => null
  private tokenRefresher: TokenRefresher = async () => null
  private reconnectAttempt = 0
  private reconnectTimer: ReturnType<typeof setTimeout> | null = null
  private intentionalClose = false
  private onStatusChange: ((status: "connecting" | "connected" | "disconnected" | "reconnecting") => void) | null = null

  configure(options: { getToken: TokenProvider; refreshToken?: TokenRefresher }) {
    this.tokenProvider = options.getToken
    if (options.refreshToken) {
      this.tokenRefresher = options.refreshToken
    }
  }

  setStatusHandler(handler: (status: "connecting" | "connected" | "disconnected" | "reconnecting") => void) {
    this.onStatusChange = handler
  }

  setMessageHandler(handler: WsMessageHandler) {
    this.handler = handler
  }

  connect() {
    this.intentionalClose = false
    void this.openSocket()
  }

  disconnect() {
    this.intentionalClose = true
    this.clearReconnect()
    if (this.socket) {
      this.socket.close()
      this.socket = null
    }
    this.onStatusChange?.("disconnected")
  }

  send(type: string, payload: Record<string, unknown>) {
    if (!this.socket || this.socket.readyState !== WebSocket.OPEN) {
      return false
    }
    this.socket.send(JSON.stringify({ type, payload }))
    return true
  }

  isConnected(): boolean {
    return this.socket?.readyState === WebSocket.OPEN
  }

  private async openSocket() {
    this.clearReconnect()
    this.onStatusChange?.(this.reconnectAttempt > 0 ? "reconnecting" : "connecting")

    let token = this.tokenProvider()
    if (!token && this.reconnectAttempt > 0) {
      token = await this.tokenRefresher()
    }
    if (!token) {
      this.onStatusChange?.("disconnected")
      return
    }

    const protocol = window.location.protocol === "https:" ? "wss:" : "ws:"
    const url = `${protocol}//${window.location.host}/api/v1/ws?token=${encodeURIComponent(token)}`
    const socket = new WebSocket(url)
    this.socket = socket

    socket.onopen = () => {
      this.reconnectAttempt = 0
      this.onStatusChange?.("connected")
    }

    socket.onmessage = (event) => {
      try {
        const envelope = JSON.parse(event.data as string) as WsEnvelope
        this.handler?.(envelope)
      } catch {
        // ignore malformed frames
      }
    }

    socket.onclose = () => {
      this.socket = null
      if (this.intentionalClose) {
        this.onStatusChange?.("disconnected")
        return
      }
      this.scheduleReconnect()
    }

    socket.onerror = () => {
      socket.close()
    }
  }

  private scheduleReconnect() {
    this.onStatusChange?.("reconnecting")
    const delay = Math.min(1000 * 2 ** this.reconnectAttempt, 30000)
    this.reconnectAttempt += 1
    this.reconnectTimer = setTimeout(() => {
      void this.openSocket()
    }, delay)
  }

  private clearReconnect() {
    if (this.reconnectTimer) {
      clearTimeout(this.reconnectTimer)
      this.reconnectTimer = null
    }
  }
}

export const wsClient = new WsClient()
