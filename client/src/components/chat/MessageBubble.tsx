import { Loader2 } from "lucide-react"
import { cn, formatMessageTime } from "@/lib/utils"

interface MessageBubbleProps {
  text: string
  createdAt: string
  isOwn: boolean
  pending?: boolean
}

export function MessageBubble({ text, createdAt, isOwn, pending }: MessageBubbleProps) {
  return (
    <div className={cn("flex w-full", isOwn ? "justify-end" : "justify-start")}>
      <div
        className={cn(
          "relative max-w-[75%] rounded-2xl px-4 py-2.5 shadow-sm",
          isOwn
            ? "rounded-br-md bg-chat-bubble-own text-primary-foreground"
            : "rounded-bl-md bg-chat-bubble-other text-foreground",
          pending && "opacity-60"
        )}
      >
        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed">{text}</p>
        <div
          className={cn(
            "mt-1 flex items-center justify-end gap-1 text-[10px]",
            isOwn ? "text-primary-foreground/70" : "text-muted-foreground"
          )}
        >
          {pending && <Loader2 className="h-3 w-3 animate-spin" />}
          <span>{formatMessageTime(createdAt)}</span>
        </div>
      </div>
    </div>
  )
}
