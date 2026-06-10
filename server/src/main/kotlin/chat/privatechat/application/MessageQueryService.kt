package chat.privatechat.application

import chat.privatechat.domain.Message
import chat.privatechat.domain.ports.MessageRepository
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.UUID

/**
 * Чтение истории сообщений (keyset pagination по created_at).
 */
@Service
class MessageQueryService(
    private val messageRepository: MessageRepository,
    private val chatService: ChatService
) {
    suspend fun listMessages(
        chatId: UUID,
        userId: UUID,
        before: Instant?,
        limit: Int
    ): List<Message> {
        chatService.requireMembership(chatId, userId)
        return messageRepository.listBefore(chatId, before, limit.coerceIn(1, 100))
    }

    suspend fun getMessage(chatId: UUID, messageId: UUID, userId: UUID): Message {
        chatService.requireMembership(chatId, userId)
        return messageRepository.findById(chatId, messageId)
            ?: throw MessageNotFoundException(chatId, messageId)
    }
}

class MessageNotFoundException(chatId: UUID, messageId: UUID) :
    RuntimeException("Message $messageId not found in chat $chatId")
