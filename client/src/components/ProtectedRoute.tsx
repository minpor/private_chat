import { useEffect, useState } from "react"
import { Navigate } from "react-router-dom"
import { getAccessTokenExpiryMs } from "@/lib/jwt"
import { useAuthStore } from "@/store/auth-store"

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  const refreshToken = useAuthStore((s) => s.refreshToken)
  const refreshTokens = useAuthStore((s) => s.refreshTokens)
  const [checking, setChecking] = useState(true)

  useEffect(() => {
    let cancelled = false

    async function ensureSession() {
      if (!accessToken) {
        if (!cancelled) setChecking(false)
        return
      }

      const expiresAt = getAccessTokenExpiryMs(accessToken)
      if (expiresAt && expiresAt <= Date.now() && refreshToken) {
        await refreshTokens()
      }

      if (!cancelled) setChecking(false)
    }

    void ensureSession()
    return () => {
      cancelled = true
    }
  }, [accessToken, refreshToken, refreshTokens])

  if (checking) {
    return null
  }

  if (!useAuthStore.getState().accessToken) {
    return <Navigate to="/auth" replace />
  }

  return children
}
