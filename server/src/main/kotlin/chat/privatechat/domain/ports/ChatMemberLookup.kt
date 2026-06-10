package chat.privatechat.domain.ports

import java.util.UUID

/**
 * Участники чата с кэшем. Снижает SELECT `chat_members` на hot path отправки сообщений.
 */
interface ChatMemberLookup {
    suspend fun findMemberIds(chatId: UUID): List<UUID>

    /** Записывает известный состав (например, после создания direct-чата). */
    suspend fun remember(chatId: UUID, memberIds: List<UUID>)
}
