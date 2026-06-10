package chat.privatechat.domain

import java.time.Instant
import java.util.UUID

/**
 * Статус принятия сообщения на write-path.
 */
enum class MessageStatus {
    ACCEPTED
}

/**
 * Текстовое сообщение в чате.
 *
 * @property clientMessageId идемпотентный ключ клиента; уникален в рамках чата.
 * @property body текст сообщения (не пустой после trim).
 */
data class Message(
    val id: UUID,
    val chatId: UUID,
    val senderId: UUID,
    val clientMessageId: UUID,
    val body: String,
    val replyTo: UUID?,
    val createdAt: Instant,
    val deletedAt: Instant?
) {
    val status: MessageStatus = MessageStatus.ACCEPTED
}

/**
 * Событие transactional outbox для асинхронной доставки (фаза 2: NATS publisher).
 */
data class OutboxEvent(
    val id: UUID,
    val eventType: String,
    val payload: String,
    val createdAt: Instant
)
