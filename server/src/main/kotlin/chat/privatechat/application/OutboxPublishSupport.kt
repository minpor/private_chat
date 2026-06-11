package chat.privatechat.application

import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.OutboxRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.util.UUID

@Service
class OutboxPublishSupport(
    private val outboxRepository: OutboxRepository
) {
    @Transactional
    suspend fun lockBatch(limit: Int): List<OutboxEvent> =
        outboxRepository.lockUnpublished(limit)

    @Transactional
    suspend fun markBatch(ids: List<UUID>, publishedAt: Instant) =
        outboxRepository.markPublishedBatch(ids, publishedAt)

    suspend fun countUnpublished(): Long =
        outboxRepository.countUnpublished()
}
