import { create } from "zustand"
import { persist } from "zustand/middleware"
import * as authApi from "@/api/auth"
import { configureApiClient } from "@/api/client"
import type { LoginRequest, RegisterRequest, UserResponse } from "@/api/types"

interface AuthState {
  accessToken: string | null
  refreshToken: string | null
  user: UserResponse | null
  setSession: (accessToken: string, refreshToken: string, user: UserResponse) => void
  clearSession: () => void
  login: (data: LoginRequest) => Promise<void>
  register: (data: RegisterRequest) => Promise<void>
  refreshTokens: () => Promise<string | null>
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      accessToken: null,
      refreshToken: null,
      user: null,

      setSession: (accessToken, refreshToken, user) => {
        set({ accessToken, refreshToken, user })
      },

      clearSession: () => {
        set({ accessToken: null, refreshToken: null, user: null })
      },

      login: async (data) => {
        const tokens = await authApi.login(data)
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          user: tokens.user
        })
      },

      register: async (data) => {
        const tokens = await authApi.register(data)
        set({
          accessToken: tokens.accessToken,
          refreshToken: tokens.refreshToken,
          user: tokens.user
        })
      },

      refreshTokens: async () => {
        const { refreshToken } = get()
        if (!refreshToken) {
          get().clearSession()
          return null
        }
        try {
          const tokens = await authApi.refresh(refreshToken)
          set({
            accessToken: tokens.accessToken,
            refreshToken: tokens.refreshToken,
            user: tokens.user
          })
          return tokens.accessToken
        } catch {
          get().clearSession()
          return null
        }
      },

      logout: () => {
        get().clearSession()
      }
    }),
    {
      name: "private-chat-auth",
      partialize: (state) => ({
        accessToken: state.accessToken,
        refreshToken: state.refreshToken,
        user: state.user
      })
    }
  )
)

configureApiClient(
  () => useAuthStore.getState().accessToken,
  () => useAuthStore.getState().refreshTokens()
)
