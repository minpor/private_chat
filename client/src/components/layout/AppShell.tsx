import { MessageSquare } from "lucide-react"
import { ChatList } from "@/components/chat/ChatList"
import { ConnectionBadge } from "@/components/common/ConnectionBadge"
import { ThemeToggle } from "@/components/common/ThemeToggle"
import { UserMenu } from "@/components/layout/UserMenu"
import { cn } from "@/lib/utils"
import { useChatStore } from "@/store/chat-store"

interface AppShellProps {
  children: React.ReactNode
}

export function AppShell({ children }: AppShellProps) {
  const sidebarOpen = useChatStore((s) => s.sidebarOpen)
  const setSidebarOpen = useChatStore((s) => s.setSidebarOpen)

  return (
    <div className="flex h-screen flex-col overflow-hidden">
      <header className="flex h-14 shrink-0 items-center justify-between border-b border-border bg-card/80 px-4 backdrop-blur-sm">
        <div className="flex items-center gap-2">
          <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary text-primary-foreground">
            <MessageSquare className="h-4 w-4" />
          </div>
          <span className="font-semibold">Private Chat</span>
        </div>
        <div className="flex items-center gap-2">
          <ConnectionBadge />
          <ThemeToggle />
          <UserMenu />
        </div>
      </header>

      <div className="relative flex min-h-0 flex-1">
        <aside
          className={cn(
            "absolute inset-y-0 left-0 z-20 w-full max-w-sm border-r border-sidebar-border bg-sidebar transition-transform md:relative md:translate-x-0 md:w-80",
            sidebarOpen ? "translate-x-0" : "-translate-x-full md:translate-x-0"
          )}
        >
          <ChatList />
        </aside>

        {sidebarOpen && (
          <button
            type="button"
            className="absolute inset-0 z-10 bg-black/40 md:hidden"
            onClick={() => setSidebarOpen(false)}
            aria-label="Закрыть меню"
          />
        )}

        <main className="flex min-w-0 flex-1 flex-col bg-background">{children}</main>
      </div>
    </div>
  )
}
