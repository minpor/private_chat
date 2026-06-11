interface TypingIndicatorProps {
  label?: string
}

export function TypingIndicator({ label = "печатает" }: TypingIndicatorProps) {
  return (
    <span className="inline-flex items-center gap-1 text-xs text-primary">
      <span>{label}</span>
      <span className="inline-flex gap-0.5">
        <span className="h-1 w-1 animate-bounce rounded-full bg-primary [animation-delay:0ms]" />
        <span className="h-1 w-1 animate-bounce rounded-full bg-primary [animation-delay:150ms]" />
        <span className="h-1 w-1 animate-bounce rounded-full bg-primary [animation-delay:300ms]" />
      </span>
    </span>
  )
}
