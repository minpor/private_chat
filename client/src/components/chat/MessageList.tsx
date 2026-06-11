import { ChevronDown } from "lucide-react"
import { useEffect, useMemo, useRef, useState } from "react"
import { DateSeparator } from "@/components/chat/DateSeparator"
import { MessageBubble } from "@/components/chat/MessageBubble"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { Skeleton } from "@/components/ui/skeleton"
import type { MessageResponse } from "@/api/types"
import { useAuthStore } from "@/store/auth-store"

interface MessageListProps {
  messages: MessageResponse[] | undefined
  isLoading: boolean
}

export function MessageList({ messages, isLoading }: MessageListProps) {
  const userId = useAuthStore((s) => s.user?.id)
  const bottomRef = useRef<HTMLDivElement>(null)
  const [showScrollDown, setShowScrollDown] = useState(false)
  const viewportRef = useRef<HTMLDivElement | null>(null)

  const sorted = useMemo(() => {
    return [...(messages ?? [])].sort(
      (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
    )
  }, [messages])

  const withSeparators = useMemo(() => {
    const result: Array<{ type: "date"; date: string } | { type: "message"; message: MessageResponse }> = []
    let lastDate = ""

    for (const message of sorted) {
      const dateKey = new Date(message.createdAt).toDateString()
      if (dateKey !== lastDate) {
        result.push({ type: "date", date: message.createdAt })
        lastDate = dateKey
      }
      result.push({ type: "message", message })
    }

    return result
  }, [sorted])

  useEffect(() => {
    if (!showScrollDown) {
      bottomRef.current?.scrollIntoView({ behavior: "smooth" })
    }
  }, [withSeparators.length, showScrollDown])

  function handleScroll(e: React.UIEvent<HTMLDivElement>) {
    const el = e.currentTarget
    const atBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 80
    setShowScrollDown(!atBottom)
  }

  if (isLoading) {
    return (
      <div className="flex flex-1 flex-col gap-3 p-4">
        {Array.from({ length: 6 }).map((_, i) => (
          <Skeleton
            key={i}
            className={`h-12 ${i % 2 === 0 ? "w-2/3 self-start" : "w-1/2 self-end"} rounded-2xl`}
          />
        ))}
      </div>
    )
  }

  if (sorted.length === 0) {
    return (
      <div className="flex flex-1 items-center justify-center p-8 text-sm text-muted-foreground">
        Нет сообщений. Напишите первым!
      </div>
    )
  }

  return (
    <div className="relative flex-1 overflow-hidden">
      <ScrollArea className="h-full">
        <div
          ref={viewportRef}
          className="flex flex-col gap-2 p-4"
          onScroll={handleScroll}
          style={{ maxHeight: "100%", overflowY: "auto" }}
        >
          {withSeparators.map((item, index) =>
            item.type === "date" ? (
              <DateSeparator key={`date-${index}`} date={item.date} />
            ) : (
              <MessageBubble
                key={item.message.id}
                text={item.message.text}
                createdAt={item.message.createdAt}
                isOwn={item.message.senderId === userId}
                pending={item.message.id.startsWith("optimistic-")}
              />
            )
          )}
          <div ref={bottomRef} />
        </div>
      </ScrollArea>

      {showScrollDown && (
        <Button
          size="icon"
          variant="secondary"
          className="absolute bottom-4 right-4 h-9 w-9 rounded-full shadow-lg"
          onClick={() => {
            setShowScrollDown(false)
            bottomRef.current?.scrollIntoView({ behavior: "smooth" })
          }}
        >
          <ChevronDown className="h-4 w-4" />
        </Button>
      )}
    </div>
  )
}
