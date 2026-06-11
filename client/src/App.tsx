import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { ThemeProvider } from "next-themes"
import { BrowserRouter, Navigate, Route, Routes } from "react-router-dom"
import { Toaster } from "sonner"
import { ProtectedRoute } from "@/components/ProtectedRoute"
import { AuthPage } from "@/pages/AuthPage"
import { ChatPage } from "@/pages/ChatPage"
import { WebSocketProvider } from "@/ws/WebSocketProvider"
import { useAuthStore } from "@/store/auth-store"

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      retry: 1
    }
  }
})

function AuthRedirect({ children }: { children: React.ReactNode }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  if (accessToken) {
    return <Navigate to="/" replace />
  }
  return children
}

export default function App() {
  return (
    <ThemeProvider attribute="class" defaultTheme="dark" enableSystem>
      <QueryClientProvider client={queryClient}>
        <BrowserRouter>
          <WebSocketProvider>
            <Routes>
              <Route
                path="/auth"
                element={
                  <AuthRedirect>
                    <AuthPage />
                  </AuthRedirect>
                }
              />
              <Route
                path="/"
                element={
                  <ProtectedRoute>
                    <ChatPage />
                  </ProtectedRoute>
                }
              />
              <Route path="*" element={<Navigate to="/" replace />} />
            </Routes>
            <Toaster richColors position="top-right" />
          </WebSocketProvider>
        </BrowserRouter>
      </QueryClientProvider>
    </ThemeProvider>
  )
}
