package chat.privatechat.application

import chat.privatechat.domain.IdGenerator
import chat.privatechat.domain.Message
import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.MessageRepository
import chat.privatechat.domain.ports.OutboxRepository
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Прямая отправка сообщения (fallback без draft-sync).
 *
 * Идемпотентность: повтор с тем же [clientMessageId] в чате возвращает существующее сообщение.
 * В одной транзакции: INSERT message + INSERT outbox.
 */
@Service
class MessageCommandService(
    private val messageRepository: MessageRepository,
    private val outboxRepository: OutboxRepository,
    private val chatService: ChatService,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper
) {
    @Transactional
    suspend fun sendDirectMessage(
        chatId: UUID,
        senderId: UUID,
        clientMessageId: UUID,
        text: String,
        replyTo: UUID?
    ): Message {
        chatService.requireMembership(chatId, senderId)

        messageRepository.findByClientMessageId(chatId, clientMessageId)?.let { return it }

        val body = text.trim()
        require(body.isNotEmpty()) { "Message text must not be empty" }
        require(body.length <= MAX_MESSAGE_LENGTH) {
            "Message text exceeds $MAX_MESSAGE_LENGTH characters"
        }

        val now = Instant.now()
        val message = Message(
            id = idGenerator.nextId(),
            chatId = chatId,
            senderId = senderId,
            clientMessageId = clientMessageId,
            body = body,
            replyTo = replyTo,
            createdAt = now,
            deletedAt = null
        )
        val saved = messageRepository.insert(message)
        outboxRepository.insert(
            OutboxEvent(
                id = idGenerator.nextId(),
                eventType = EVENT_MESSAGE_CREATED,
                payload = objectMapper.writeValueAsString(
                    mapOf(
                        "messageId" to saved.id.toString(),
                        "chatId" to saved.chatId.toString(),
                        "senderId" to saved.senderId.toString(),
                        "text" to saved.body,
                        "createdAt" to saved.createdAt.toString()
                    )
                ),
                createdAt = now
            )
        )
        return saved
    }

    companion object {
        const val EVENT_MESSAGE_CREATED = "message.created"
        const val MAX_MESSAGE_LENGTH = 8192
    }
}
