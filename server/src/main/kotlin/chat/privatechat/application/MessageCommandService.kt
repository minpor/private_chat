package chat.privatechat.application

import chat.privatechat.domain.DraftSnapshot
import chat.privatechat.domain.IdGenerator
import chat.privatechat.domain.Message
import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.ChatMemberLookup
import chat.privatechat.infrastructure.observability.ChatMetrics.MessageSource
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.springframework.stereotype.Service
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
    private val messageWriteSupport: MessageWriteSupport,
    private val chatMemberLookup: ChatMemberLookup,
    private val directMessageWriteGate: DirectMessageWriteGate,
    private val draftService: DraftService,
    private val rateLimitService: RateLimitService,
    private val idGenerator: IdGenerator
) {
    suspend fun sendDirectMessage(
        chatId: UUID,
        senderId: UUID,
        clientMessageId: UUID,
        text: String,
        replyTo: UUID?
    ): Message {
        val memberIds = directMessageWriteGate.checkLimitAndFindMembers(senderId, chatId)

        return persistMessage(
            chatId = chatId,
            senderId = senderId,
            clientMessageId = clientMessageId,
            body = text,
            replyTo = replyTo,
            memberIds = memberIds,
            source = MessageSource.DIRECT
        )
    }

    /**
     * Фиксирует сообщение из серверного снимка черновика в Redis.
     *
     * @throws DraftNotFoundException если TTL истёк или черновик удалён
     */
    suspend fun commitFromDraft(
        snapshot: DraftSnapshot,
        clientMessageId: UUID,
        expectedRevision: Long?
    ): Message {
        rateLimitService.checkCommitLimit(snapshot.userId)
        draftService.validateRevision(snapshot, expectedRevision)

        val message = persistMessage(
            chatId = snapshot.chatId,
            senderId = snapshot.userId,
            clientMessageId = clientMessageId,
            body = snapshot.text,
            replyTo = null,
            memberIds = null,
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
        memberIds: List<UUID>?,
        source: MessageSource
    ): Message {
        val trimmed = body.trim()
        require(trimmed.isNotEmpty()) { "Message text must not be empty" }
        require(trimmed.length <= MAX_MESSAGE_LENGTH) {
            "Message text exceeds $MAX_MESSAGE_LENGTH characters"
        }

        val resolvedMemberIds = memberIds ?: chatMemberLookup.findMemberIds(chatId)
        if (senderId !in resolvedMemberIds) {
            throw ChatAccessDeniedException(chatId)
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
        val outboxEvent = buildOutboxEvent(message, resolvedMemberIds, now)
        return messageWriteSupport.persistMessageAndOutbox(
            message = message,
            outboxEvent = outboxEvent,
            source = source,
            membershipVerified = true
        )
    }

    private fun buildOutboxEvent(
        message: Message,
        memberIds: List<UUID>,
        createdAt: Instant
    ): OutboxEvent {
        val natsPayload = outboxJson.encodeToString(
            MessageCreatedNatsPayload(
                messageId = message.id.toString(),
                chatId = message.chatId.toString(),
                senderId = message.senderId.toString(),
                text = message.body,
                createdAt = message.createdAt.toString(),
                memberIds = memberIds.map { it.toString() }
            )
        ).toByteArray(Charsets.UTF_8)
        return OutboxEvent(
            id = idGenerator.nextId(),
            eventType = EVENT_MESSAGE_CREATED,
            payload = String(natsPayload, Charsets.UTF_8),
            createdAt = createdAt,
            natsPayload = natsPayload
        )
    }

    companion object {
        const val EVENT_MESSAGE_CREATED = "message.created"
        const val MAX_MESSAGE_LENGTH = 8192
        private val outboxJson = Json { encodeDefaults = true }
    }
}
