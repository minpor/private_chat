package chat.privatechat.application

import chat.privatechat.domain.DraftSnapshot
import java.util.UUID

class DraftNotFoundException(
    val chatId: UUID? = null,
    val draftId: UUID? = null
) : RuntimeException(
    when {
        draftId != null -> "Draft not found: $draftId"
        chatId != null -> "Draft not found for chat: $chatId"
        else -> "Draft not found"
    }
)

class DraftRevisionConflictException(
    val current: DraftSnapshot
) : RuntimeException("Draft revision conflict: expected newer than ${current.revision}")

class RateLimitExceededException(
    val retryAfterSeconds: Long
) : RuntimeException("Rate limit exceeded, retry after $retryAfterSeconds seconds")
