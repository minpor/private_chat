import { api } from "./client"
import type { LoginRequest, RegisterRequest, TokenResponse, UserResponse } from "./types"

export function register(data: RegisterRequest): Promise<TokenResponse> {
  return api.post<TokenResponse>("/auth/register", data)
}

export function login(data: LoginRequest): Promise<TokenResponse> {
  return api.post<TokenResponse>("/auth/login", data)
}

export function refresh(refreshToken: string): Promise<TokenResponse> {
  return api.post<TokenResponse>("/auth/refresh", { refreshToken })
}

export function getMe(): Promise<UserResponse> {
  return api.get<UserResponse>("/users/me")
}
