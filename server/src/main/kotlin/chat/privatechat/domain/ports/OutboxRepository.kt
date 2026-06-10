package chat.privatechat.domain.ports

import chat.privatechat.domain.OutboxEvent
import java.time.Instant
import java.util.UUID

/**
 * Transactional outbox — запись в той же транзакции, что и сообщение.
 */
interface OutboxRepository {
    suspend fun insert(event: OutboxEvent)

    suspend fun lockUnpublished(limit: Int): List<OutboxEvent>

    suspend fun markPublishedBatch(ids: List<UUID>, publishedAt: Instant)
}
