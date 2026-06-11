import { Plus } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { ApiClientError } from "@/api/client"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger
} from "@/components/ui/dialog"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useCreateChat } from "@/hooks/use-chats"

export function NewChatDialog() {
  const [open, setOpen] = useState(false)
  const [username, setUsername] = useState("")
  const createChat = useCreateChat()

  async function handleCreate(e: React.FormEvent) {
    e.preventDefault()
    if (username.trim().length < 3) {
      toast.error("Username от 3 символов")
      return
    }
    try {
      await createChat.mutateAsync(username.trim())
      setOpen(false)
      setUsername("")
    } catch (err) {
      const message = err instanceof ApiClientError ? err.message : "Не удалось создать чат"
      toast.error(message)
    }
  }

  return (
    <Dialog open={open} onOpenChange={setOpen}>
      <DialogTrigger asChild>
        <Button size="sm" className="gap-1.5">
          <Plus className="h-4 w-4" />
          Новый чат
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Новый direct-чат</DialogTitle>
          <DialogDescription>Введите username собеседника</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleCreate} className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="peer-username">Username</Label>
            <Input
              id="peer-username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              placeholder="bob"
              autoFocus
            />
          </div>
          <Button type="submit" className="w-full" disabled={createChat.isPending}>
            {createChat.isPending ? "Создание..." : "Создать"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
