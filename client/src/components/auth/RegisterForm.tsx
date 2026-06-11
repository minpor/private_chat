import { useState } from "react"
import { toast } from "sonner"
import { ApiClientError } from "@/api/client"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuthStore } from "@/store/auth-store"

export function RegisterForm() {
  const register = useAuthStore((s) => s.register)
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [displayName, setDisplayName] = useState("")
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (username.length < 3 || password.length < 8 || !displayName.trim()) {
      toast.error("Username от 3 символов, password от 8, укажите имя")
      return
    }
    setLoading(true)
    try {
      await register({ username, password, displayName: displayName.trim() })
    } catch (err) {
      const message = err instanceof ApiClientError ? err.message : "Ошибка регистрации"
      toast.error(message)
    } finally {
      setLoading(false)
    }
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div className="space-y-2">
        <Label htmlFor="reg-username">Username</Label>
        <Input
          id="reg-username"
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          autoComplete="username"
          required
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="reg-display">Display name</Label>
        <Input
          id="reg-display"
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          autoComplete="name"
          required
        />
      </div>
      <div className="space-y-2">
        <Label htmlFor="reg-password">Password</Label>
        <Input
          id="reg-password"
          type="password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          autoComplete="new-password"
          required
        />
      </div>
      <Button type="submit" className="w-full" disabled={loading}>
        {loading ? "Регистрация..." : "Зарегистрироваться"}
      </Button>
    </form>
  )
}
