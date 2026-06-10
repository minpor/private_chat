package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatMember
import chat.privatechat.domain.ChatType
import chat.privatechat.domain.ports.ChatRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Repository
class R2dbcChatRepository(
    private val databaseClient: DatabaseClient
) : ChatRepository {

    @Transactional
    override suspend fun insert(chat: Chat, memberUserIds: List<UUID>): Chat {
        databaseClient.sql(
            """
            INSERT INTO chats (id, type, title, created_by)
            VALUES (:id, :type::chat_type, :title, :created_by)
            """.trimIndent()
        )
            .bind("id", chat.id)
            .bind("type", chat.type.name.lowercase())
            .bindNullable("title", chat.title)
            .bind("created_by", chat.createdBy)
            .fetch()
            .rowsUpdated()
            .awaitSingle()

        memberUserIds.forEach { memberId ->
            databaseClient.sql(
                """
                INSERT INTO chat_members (chat_id, user_id)
                VALUES (:chat_id, :user_id)
                """.trimIndent()
            )
                .bind("chat_id", chat.id)
                .bind("user_id", memberId)
                .fetch()
                .rowsUpdated()
                .awaitSingle()
        }
        return chat
    }

    override suspend fun findById(id: UUID): Chat? =
        databaseClient.sql(
            """
            SELECT id, type, title, created_by, created_at
            FROM chats
            WHERE id = :id
            """.trimIndent()
        )
            .bind("id", id)
            .map { row, _ -> row.toChat() }
            .awaitOneOrNull()

    override suspend fun isMember(chatId: UUID, userId: UUID): Boolean =
        databaseClient.sql(
            """
            SELECT 1 AS one
            FROM chat_members
            WHERE chat_id = :chat_id AND user_id = :user_id
            LIMIT 1
            """.trimIndent()
        )
            .bind("chat_id", chatId)
            .bind("user_id", userId)
            .map { _, _ -> true }
            .awaitOneOrNull() ?: false

    override suspend fun findMembers(chatId: UUID): List<ChatMember> =
        databaseClient.sql(
            """
            SELECT chat_id, user_id, joined_at
            FROM chat_members
            WHERE chat_id = :chat_id
            """.trimIndent()
        )
            .bind("chat_id", chatId)
            .map { row, _ -> row.toChatMember() }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun findDirectChatBetween(userId: UUID, otherUserId: UUID): Chat? =
        databaseClient.sql(
            """
            SELECT c.id, c.type, c.title, c.created_by, c.created_at
            FROM chats c
            JOIN chat_members m1 ON m1.chat_id = c.id AND m1.user_id = :user_id
            JOIN chat_members m2 ON m2.chat_id = c.id AND m2.user_id = :other_user_id
            WHERE c.type = 'direct'
            LIMIT 1
            """.trimIndent()
        )
            .bind("user_id", userId)
            .bind("other_user_id", otherUserId)
            .map { row, _ -> row.toChat() }
            .awaitOneOrNull()

    override suspend fun listForUser(userId: UUID, limit: Int): List<Chat> =
        databaseClient.sql(
            """
            SELECT c.id, c.type, c.title, c.created_by, c.created_at
            FROM chats c
            JOIN chat_members m ON m.chat_id = c.id
            WHERE m.user_id = :user_id
            ORDER BY c.created_at DESC
            LIMIT :limit
            """.trimIndent()
        )
            .bind("user_id", userId)
            .bind("limit", limit)
            .map { row, _ -> row.toChat() }
            .all()
            .collectList()
            .awaitSingle()
}
