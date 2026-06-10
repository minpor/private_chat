package chat.privatechat.application

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatType
import chat.privatechat.domain.IdGenerator
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
    private val idGenerator: IdGenerator
) {
    suspend fun createDirectChat(requesterId: UUID, otherUsername: String): Chat {
        val otherUser = userRepository.findByUsername(otherUsername)
            ?: throw UserNotFoundException(otherUsername)
        require(requesterId != otherUser.id) { "Cannot create direct chat with yourself" }

        chatRepository.findDirectChatBetween(requesterId, otherUser.id)?.let { return it }

        val chat = Chat(
            id = idGenerator.nextId(),
            type = ChatType.DIRECT,
            title = null,
            createdBy = requesterId,
            createdAt = Instant.now()
        )
        return chatRepository.insert(chat, listOf(requesterId, otherUser.id))
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

    suspend fun requireMembership(chatId: UUID, userId: UUID) {
        if (!chatRepository.isMember(chatId, userId)) {
            throw ChatAccessDeniedException(chatId)
        }
    }
}

class UserNotFoundException(username: String) : RuntimeException("User not found: $username")

class ChatNotFoundException(chatId: UUID) : RuntimeException("Chat not found: $chatId")

class ChatAccessDeniedException(chatId: UUID) : RuntimeException("Access denied to chat: $chatId")
