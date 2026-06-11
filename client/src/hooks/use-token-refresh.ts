import { useEffect } from "react"
import { getAccessTokenExpiryMs } from "@/lib/jwt"
import { useAuthStore } from "@/store/auth-store"

const REFRESH_BEFORE_MS = 60_000

export function useTokenRefresh() {
  const accessToken = useAuthStore((s) => s.accessToken)
  const refreshTokens = useAuthStore((s) => s.refreshTokens)

  useEffect(() => {
    if (!accessToken) return

    const expiresAt = getAccessTokenExpiryMs(accessToken)
    if (!expiresAt) return

    const delay = expiresAt - Date.now() - REFRESH_BEFORE_MS
    const runRefresh = () => {
      void refreshTokens()
    }

    if (delay <= 0) {
      runRefresh()
      return
    }

    const timer = setTimeout(runRefresh, delay)
    return () => clearTimeout(timer)
  }, [accessToken, refreshTokens])
}
