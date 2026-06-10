package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.Message
import chat.privatechat.domain.ports.MessageRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class R2dbcMessageRepository(
    private val databaseClient: DatabaseClient
) : MessageRepository {

    override suspend fun findById(chatId: UUID, messageId: UUID): Message? =
        databaseClient.sql(
            """
            SELECT id, chat_id, sender_id, client_msg_id, body, reply_to, created_at, deleted_at
            FROM messages
            WHERE chat_id = :chat_id AND id = :id
            """.trimIndent()
        )
            .bind("chat_id", chatId)
            .bind("id", messageId)
            .map { row, _ -> row.toMessage() }
            .awaitOneOrNull()

    override suspend fun findByClientMessageId(chatId: UUID, clientMessageId: UUID): Message? =
        databaseClient.sql(
            """
            SELECT id, chat_id, sender_id, client_msg_id, body, reply_to, created_at, deleted_at
            FROM messages
            WHERE chat_id = :chat_id AND client_msg_id = :client_msg_id
            """.trimIndent()
        )
            .bind("chat_id", chatId)
            .bind("client_msg_id", clientMessageId)
            .map { row, _ -> row.toMessage() }
            .awaitOneOrNull()

    override suspend fun insert(message: Message): Message =
        databaseClient.sql(
            """
            INSERT INTO messages (id, chat_id, sender_id, client_msg_id, body, reply_to, created_at)
            VALUES (:id, :chat_id, :sender_id, :client_msg_id, :body, :reply_to, :created_at)
            RETURNING id, chat_id, sender_id, client_msg_id, body, reply_to, created_at, deleted_at
            """.trimIndent()
        )
            .bind("id", message.id)
            .bind("chat_id", message.chatId)
            .bind("sender_id", message.senderId)
            .bind("client_msg_id", message.clientMessageId)
            .bind("body", message.body)
            .bindNullable("reply_to", message.replyTo)
            .bind("created_at", message.createdAt)
            .map { row, _ -> row.toMessage() }
            .one()
            .awaitSingle()

    override suspend fun listBefore(chatId: UUID, before: Instant?, limit: Int): List<Message> {
        val sql = if (before == null) {
            """
            SELECT id, chat_id, sender_id, client_msg_id, body, reply_to, created_at, deleted_at
            FROM messages
            WHERE chat_id = :chat_id AND deleted_at IS NULL
            ORDER BY created_at DESC
            LIMIT :limit
            """.trimIndent()
        } else {
            """
            SELECT id, chat_id, sender_id, client_msg_id, body, reply_to, created_at, deleted_at
            FROM messages
            WHERE chat_id = :chat_id AND deleted_at IS NULL AND created_at < :before
            ORDER BY created_at DESC
            LIMIT :limit
            """.trimIndent()
        }

        var spec = databaseClient.sql(sql)
            .bind("chat_id", chatId)
            .bind("limit", limit)
        if (before != null) {
            spec = spec.bind("before", before)
        }
        return spec
            .map { row, _ -> row.toMessage() }
            .all()
            .collectList()
            .awaitSingle()
    }
}
