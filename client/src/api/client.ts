import type { ApiError } from "./types"

const API_BASE = "/api/v1"

const PUBLIC_AUTH_PATHS = ["/auth/register", "/auth/login", "/auth/refresh"]

function isPublicAuthPath(path: string): boolean {
  return PUBLIC_AUTH_PATHS.some((publicPath) => path.startsWith(publicPath))
}

type TokenGetter = () => string | null
type TokenRefresher = () => Promise<string | null>

let getAccessToken: TokenGetter = () => null
let refreshAccessToken: TokenRefresher = async () => null

export function configureApiClient(getter: TokenGetter, refresher: TokenRefresher) {
  getAccessToken = getter
  refreshAccessToken = refresher
}

export class ApiClientError extends Error {
  status: number
  body: ApiError

  constructor(status: number, body: ApiError) {
    super(body.detail ?? body.title ?? `HTTP ${status}`)
    this.status = status
    this.body = body
  }
}

async function parseError(response: Response): Promise<ApiError> {
  try {
    return (await response.json()) as ApiError
  } catch {
    return { status: response.status, detail: response.statusText }
  }
}

async function request<T>(
  path: string,
  init: RequestInit = {},
  retry = true
): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has("Content-Type") && init.body) {
    headers.set("Content-Type", "application/json")
  }

  const token = getAccessToken()
  if (token && !isPublicAuthPath(path)) {
    headers.set("Authorization", `Bearer ${token}`)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers
  })

  if (response.status === 401 && retry) {
    const newToken = await refreshAccessToken()
    if (newToken) {
      return request<T>(path, init, false)
    }
  }

  if (!response.ok) {
    throw new ApiClientError(response.status, await parseError(response))
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "POST", body: body ? JSON.stringify(body) : undefined }),
  put: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: "PUT", body: body ? JSON.stringify(body) : undefined }),
  delete: <T>(path: string) => request<T>(path, { method: "DELETE" })
}
