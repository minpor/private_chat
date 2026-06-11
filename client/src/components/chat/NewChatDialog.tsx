import { Loader2, Plus, Search } from "lucide-react"
import { useState } from "react"
import { toast } from "sonner"
import { ApiClientError } from "@/api/client"
import type { UserResponse } from "@/api/types"
import { Avatar, AvatarFallback } from "@/components/ui/avatar"
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
import { ScrollArea } from "@/components/ui/scroll-area"
import { useCreateChat } from "@/hooks/use-chats"
import { useUserSearch } from "@/hooks/use-user-search"
import { initials } from "@/lib/utils"

export function NewChatDialog() {
  const [open, setOpen] = useState(false)
  const [query, setQuery] = useState("")
  const createChat = useCreateChat()
  const { data: results, isFetching } = useUserSearch(query, open)

  function reset() {
    setQuery("")
  }

  async function startChat(username: string) {
    try {
      await createChat.mutateAsync(username)
      setOpen(false)
      reset()
    } catch (err) {
      const message = err instanceof ApiClientError ? err.message : "Не удалось создать чат"
      toast.error(message)
    }
  }

  function handleSelect(user: UserResponse) {
    void startChat(user.username)
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    const username = query.trim()
    if (username.length < 3) {
      toast.error("Введите минимум 3 символа username")
      return
    }
    void startChat(username)
  }

  const trimmed = query.trim()
  const showHint = trimmed.length > 0 && trimmed.length < 2
  const showEmpty = trimmed.length >= 2 && !isFetching && (results?.length ?? 0) === 0

  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next)
        if (!next) reset()
      }}
    >
      <DialogTrigger asChild>
        <Button size="sm" className="gap-1.5">
          <Plus className="h-4 w-4" />
          Новый чат
        </Button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Новый direct-чат</DialogTitle>
          <DialogDescription>Найдите контакт по username и начните переписку</DialogDescription>
        </DialogHeader>
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Поиск по username..."
              className="pl-9"
              autoFocus
            />
          </div>

          <ScrollArea className="h-48 rounded-md border border-border">
            <div className="p-1">
              {showHint && (
                <p className="p-3 text-sm text-muted-foreground">Введите ещё символ для поиска</p>
              )}
              {isFetching && trimmed.length >= 2 && (
                <div className="flex items-center justify-center gap-2 p-6 text-sm text-muted-foreground">
                  <Loader2 className="h-4 w-4 animate-spin" />
                  Поиск...
                </div>
              )}
              {showEmpty && (
                <p className="p-3 text-sm text-muted-foreground">
                  Никого не найдено. Можно создать чат по точному username.
                </p>
              )}
              {results?.map((user) => (
                <button
                  key={user.id}
                  type="button"
                  onClick={() => handleSelect(user)}
                  disabled={createChat.isPending}
                  className="flex w-full items-center gap-3 rounded-lg px-3 py-2.5 text-left transition-colors hover:bg-muted disabled:opacity-50"
                >
                  <Avatar className="h-9 w-9">
                    <AvatarFallback>{initials(user.displayName || user.username)}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0">
                    <p className="truncate font-medium">{user.displayName}</p>
                    <p className="truncate text-xs text-muted-foreground">@{user.username}</p>
                  </div>
                </button>
              ))}
            </div>
          </ScrollArea>

          <Button type="submit" className="w-full" disabled={createChat.isPending || trimmed.length < 3}>
            {createChat.isPending ? "Создание..." : "Создать чат по username"}
          </Button>
        </form>
      </DialogContent>
    </Dialog>
  )
}
