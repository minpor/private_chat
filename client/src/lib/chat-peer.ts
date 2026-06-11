import type { ChatPeerResponse, ChatResponse } from "@/api/types"

export function peerLabel(peer: ChatPeerResponse | null | undefined): string | null {
  if (!peer) return null
  return peer.displayName.trim() || peer.username
}

export function chatTitle(chat: ChatResponse, peers: Record<string, ChatPeerResponse>): string {
  const peer = peers[chat.id] ?? chat.peer
  const label = peerLabel(peer)
  if (label) return label
  if (chat.title) return chat.title
  return `Chat ${chat.id.slice(0, 8)}`
}
