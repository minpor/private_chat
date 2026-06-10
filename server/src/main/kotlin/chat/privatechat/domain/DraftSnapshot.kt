package chat.privatechat.domain

import java.time.Instant
import java.util.UUID

/**
 * Эфемерный снимок черновика в Redis до [chat.privatechat.application.MessageCommandService.commitFromDraft].
 *
 * @property revision монотонно растёт с каждым patch; last-write-wins при нескольких устройствах.
 */
data class DraftSnapshot(
    val draftId: UUID,
    val chatId: UUID,
    val userId: UUID,
    val text: String,
    val revision: Long,
    val clientSessionId: String?,
    val updatedAt: Instant
)
