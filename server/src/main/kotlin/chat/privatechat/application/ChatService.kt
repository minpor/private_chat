package chat.privatechat.application

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatType
import chat.privatechat.domain.IdGenerator
import chat.privatechat.domain.User
import chat.privatechat.domain.ports.ChatMemberLookup
import chat.privatechat.domain.ports.ChatRepository
import chat.privatechat.domain.ports.UserRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Создание и просмотр чатов. Direct-чат идемпотентен для пары пользователей.
 */
@Service
class ChatService(
    private val chatRepository: ChatRepository,
    private val userRepository: UserRepository,
    private val chatMemberLookup: ChatMemberLookup,
    private val idGenerator: IdGenerator
) {
    suspend fun createDirectChat(requesterId: UUID, otherUsername: String): Chat {
        val otherUser = userRepository.findByUsername(otherUsername)
            ?: throw UserNotFoundException(otherUsername)
        require(requesterId != otherUser.id) { "Cannot create direct chat with yourself" }

        chatRepository.findDirectChatBetween(requesterId, otherUser.id)?.let { chat ->
            chatMemberLookup.remember(chat.id, listOf(requesterId, otherUser.id))
            return chat
        }

        val chat = Chat(
            id = idGenerator.nextId(),
            type = ChatType.DIRECT,
            title = null,
            createdBy = requesterId,
            createdAt = Instant.now()
        )
        val memberIds = listOf(requesterId, otherUser.id)
        val inserted = chatRepository.insert(chat, memberIds)
        chatMemberLookup.remember(inserted.id, memberIds)
        return inserted
    }

    suspend fun getChat(chatId: UUID, userId: UUID): Chat {
        val chat = chatRepository.findById(chatId) ?: throw ChatNotFoundException(chatId)
        if (!chatRepository.isMember(chatId, userId)) {
            throw ChatAccessDeniedException(chatId)
        }
        return chat
    }

    suspend fun listChats(userId: UUID, limit: Int = 50): List<Chat> =
        chatRepository.listForUser(userId, limit.coerceIn(1, 100))

    suspend fun resolveDirectChatPeer(chat: Chat, currentUserId: UUID): User? {
        if (chat.type != ChatType.DIRECT) return null
        val peerId = chatMemberLookup.findMemberIds(chat.id).firstOrNull { it != currentUserId }
            ?: return null
        return userRepository.findById(peerId)
    }

    suspend fun requireMembership(chatId: UUID, userId: UUID) {
        if (!chatRepository.isMember(chatId, userId)) {
            throw ChatAccessDeniedException(chatId)
        }
    }
}

class UserNotFoundException(username: String) : RuntimeException("User not found: $username")

class ChatNotFoundException(chatId: UUID) : RuntimeException("Chat not found: $chatId")

class ChatAccessDeniedException(chatId: UUID) : RuntimeException("Access denied to chat: $chatId")
