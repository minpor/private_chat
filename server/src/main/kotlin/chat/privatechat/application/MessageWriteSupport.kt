package chat.privatechat.application

import chat.privatechat.domain.Message
import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.MessageRepository
import chat.privatechat.domain.ports.OutboxRepository
import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.observability.ChatMetrics.MessageSource
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MessageWriteSupport(
    private val messageRepository: MessageRepository,
    private val outboxRepository: OutboxRepository,
    private val chatMetrics: ChatMetrics
) {
    @Transactional
    suspend fun persistMessageAndOutbox(
        message: Message,
        outboxEvent: OutboxEvent,
        source: MessageSource
    ): Message {
        val saved = messageRepository.insertForSender(message)
        outboxRepository.insert(outboxEvent)
        chatMetrics.recordMessageAccepted(source)
        return saved
    }
}
