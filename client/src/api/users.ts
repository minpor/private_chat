import { api } from "./client"
import type { UserResponse } from "./types"

export function searchUsers(query: string, limit = 20): Promise<UserResponse[]> {
  const params = new URLSearchParams({ q: query, limit: String(limit) })
  return api.get<UserResponse[]>(`/users/search?${params}`)
}
