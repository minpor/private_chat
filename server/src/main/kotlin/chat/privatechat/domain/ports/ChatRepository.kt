package chat.privatechat.domain.ports

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatMember
import chat.privatechat.domain.ChatType
import java.util.UUID

/**
 * Порт метаданных чатов и участников.
 */
interface ChatRepository {
    suspend fun findById(id: UUID): Chat?

    suspend fun insert(chat: Chat, memberUserIds: List<UUID>): Chat

    suspend fun isMember(chatId: UUID, userId: UUID): Boolean

    suspend fun findMembers(chatId: UUID): List<ChatMember>

    suspend fun findDirectChatBetween(userId: UUID, otherUserId: UUID): Chat?

    suspend fun listForUser(userId: UUID, limit: Int): List<Chat>
}

data class NewChat(
    val id: UUID,
    val type: ChatType,
    val title: String?,
    val createdBy: UUID,
    val memberUserIds: List<UUID>
)
