package chat.privatechat.domain.ports

import chat.privatechat.domain.OutboxEvent

/**
 * Transactional outbox — запись в той же транзакции, что и сообщение.
 */
interface OutboxRepository {
    suspend fun insert(event: OutboxEvent)
}
