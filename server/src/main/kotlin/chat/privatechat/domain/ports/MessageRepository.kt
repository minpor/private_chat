package chat.privatechat.domain.ports

import chat.privatechat.domain.Message
import java.time.Instant
import java.util.UUID

/**
 * Порт хранения сообщений (PostgreSQL, R2DBC).
 */
interface MessageRepository {
    suspend fun findById(chatId: UUID, messageId: UUID): Message?

    suspend fun findByClientMessageId(chatId: UUID, clientMessageId: UUID): Message?

    suspend fun insert(message: Message): Message

    suspend fun listBefore(
        chatId: UUID,
        before: Instant?,
        limit: Int
    ): List<Message>
}
