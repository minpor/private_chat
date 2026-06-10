package chat.privatechat.application

import chat.privatechat.domain.DraftSnapshot
import chat.privatechat.domain.IdGenerator
import chat.privatechat.domain.Message
import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.MessageRepository
import chat.privatechat.domain.ports.OutboxRepository
import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.observability.ChatMetrics.MessageSource
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

/**
 * Отправка сообщений: прямой POST (fallback) и commit из draft-sync.
 *
 * Идемпотентность: повтор с тем же [clientMessageId] в чате возвращает существующее сообщение.
 * В одной транзакции: INSERT message + INSERT outbox.
 */
@Service
@Suppress("LongParameterList")
class MessageCommandService(
    private val messageRepository: MessageRepository,
    private val outboxRepository: OutboxRepository,
    private val chatService: ChatService,
    private val draftService: DraftService,
    private val rateLimitService: RateLimitService,
    private val idGenerator: IdGenerator,
    private val objectMapper: ObjectMapper,
    private val chatMetrics: ChatMetrics
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
        rateLimitService.checkDirectMessageLimit(senderId)

        return persistMessage(
            chatId = chatId,
            senderId = senderId,
            clientMessageId = clientMessageId,
            body = text,
            replyTo = replyTo,
            source = MessageSource.DIRECT
        )
    }

    /**
     * Фиксирует сообщение из серверного снимка черновика в Redis.
     *
     * @throws DraftNotFoundException если TTL истёк или черновик удалён
     */
    @Transactional
    suspend fun commitFromDraft(
        snapshot: DraftSnapshot,
        clientMessageId: UUID,
        expectedRevision: Long?
    ): Message {
        chatService.requireMembership(snapshot.chatId, snapshot.userId)
        rateLimitService.checkCommitLimit(snapshot.userId)
        draftService.validateRevision(snapshot, expectedRevision)

        val message = persistMessage(
            chatId = snapshot.chatId,
            senderId = snapshot.userId,
            clientMessageId = clientMessageId,
            body = snapshot.text,
            replyTo = null,
            source = MessageSource.DRAFT
        )
        draftService.deleteAfterCommit(snapshot)
        return message
    }

    private suspend fun persistMessage(
        chatId: UUID,
        senderId: UUID,
        clientMessageId: UUID,
        body: String,
        replyTo: UUID?,
        source: MessageSource
    ): Message {
        messageRepository.findByClientMessageId(chatId, clientMessageId)?.let { return it }

        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "Message text must not be empty" }
        require(trimmed.length <= MAX_MESSAGE_LENGTH) {
            "Message text exceeds $MAX_MESSAGE_LENGTH characters"
        }

        val now = Instant.now()
        val message = Message(
            id = idGenerator.nextId(),
            chatId = chatId,
            senderId = senderId,
            clientMessageId = clientMessageId,
            body = trimmed,
            replyTo = replyTo,
            createdAt = now,
            deletedAt = null
        )
        val saved = messageRepository.insert(message)
        outboxRepository.insert(buildOutboxEvent(saved, now))
        chatMetrics.recordMessageAccepted(source)
        return saved
    }

    private fun buildOutboxEvent(message: Message, createdAt: Instant): OutboxEvent =
        OutboxEvent(
            id = idGenerator.nextId(),
            eventType = EVENT_MESSAGE_CREATED,
            payload = objectMapper.writeValueAsString(
                mapOf(
                    "messageId" to message.id.toString(),
                    "chatId" to message.chatId.toString(),
                    "senderId" to message.senderId.toString(),
                    "text" to message.body,
                    "createdAt" to message.createdAt.toString()
                )
            ),
            createdAt = createdAt
        )

    companion object {
        const val EVENT_MESSAGE_CREATED = "message.created"
        const val MAX_MESSAGE_LENGTH = 8192
    }
}
