import { Badge } from "@/components/ui/badge"
import { useChatStore } from "@/store/chat-store"

const labels = {
  connected: { text: "Online", variant: "success" as const },
  connecting: { text: "Connecting", variant: "warning" as const },
  reconnecting: { text: "Reconnecting", variant: "warning" as const },
  disconnected: { text: "Offline", variant: "destructive" as const }
}

export function ConnectionBadge() {
  const wsStatus = useChatStore((s) => s.wsStatus)
  const { text, variant } = labels[wsStatus]

  return (
    <Badge variant={variant} className="gap-1.5">
      <span
        className={`h-1.5 w-1.5 rounded-full ${
          wsStatus === "connected"
            ? "bg-emerald-500 animate-pulse"
            : wsStatus === "disconnected"
              ? "bg-destructive"
              : "bg-amber-500 animate-pulse"
        }`}
      />
      {text}
    </Badge>
  )
}
