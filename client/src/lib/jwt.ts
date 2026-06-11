export function getAccessTokenExpiryMs(token: string): number | null {
  try {
    const payload = JSON.parse(atob(token.split(".")[1] ?? "")) as { exp?: number }
    if (!payload.exp) return null
    return payload.exp * 1000
  } catch {
    return null
  }
}
