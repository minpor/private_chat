import { useQuery } from "@tanstack/react-query"
import * as usersApi from "@/api/users"

export function useUserSearch(query: string, enabled = true) {
  const trimmed = query.trim()

  return useQuery({
    queryKey: ["users", "search", trimmed],
    queryFn: () => usersApi.searchUsers(trimmed),
    enabled: enabled && trimmed.length >= 2,
    staleTime: 30_000
  })
}
