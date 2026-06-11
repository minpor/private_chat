export interface UserResponse {
  id: string
  username: string
  displayName: string
}

export interface TokenResponse {
  accessToken: string
  refreshToken: string
  tokenType: string
  user: UserResponse
}

export interface RegisterRequest {
  username: string
  password: string
  displayName: string
}

export interface LoginRequest {
  username: string
  password: string
}

export interface ChatResponse {
  id: string
  type: string
  title: string | null
  createdBy: string
  createdAt: string
}

export interface MessageResponse {
  id: string
  chatId: string
  senderId: string
  clientMessageId: string
  text: string
  replyTo: string | null
  createdAt: string
}

export interface MessageListResponse {
  messages: MessageResponse[]
  nextBefore: string | null
}

export interface MessageAcceptedResponse {
  id: string
  chatId: string
  clientMessageId: string
  status: string
  createdAt: string
}

export interface ApiError {
  title?: string
  detail?: string
  status?: number
}

export type WsStatus = "connecting" | "connected" | "disconnected" | "reconnecting"

export type SendMode = "draft" | "rest"

export interface WsEnvelope {
  type: string
  payload?: Record<string, unknown>
}

export interface MessageNewPayload {
  id: string
  chatId: string
  senderId: string
  text: string
  createdAt: string
}

export interface WsErrorPayload {
  code: string
  message: string
  retryAfterSeconds?: number
}

export interface TypingIndicatorPayload {
  chatId: string
  userId: string
  active: boolean
}

export type DisplayMessage = MessageResponse & {
  pending?: boolean
  optimistic?: boolean
}
