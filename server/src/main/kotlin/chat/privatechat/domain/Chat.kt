package chat.privatechat.domain

import java.time.Instant
import java.util.UUID

/**
 * Тип чата: личная переписка или группа.
 */
enum class ChatType {
    DIRECT,
    GROUP
}

/**
 * Чат с участниками.
 *
 * @property id UUID v7.
 * @property type direct — ровно два участника; group — больше.
 * @property title название группы; для direct может быть null.
 */
data class Chat(
    val id: UUID,
    val type: ChatType,
    val title: String?,
    val createdBy: UUID,
    val createdAt: Instant
)

/**
 * Участник чата.
 */
data class ChatMember(
    val chatId: UUID,
    val userId: UUID,
    val joinedAt: Instant
)
