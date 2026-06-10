package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.OutboxRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

/**
 * Запись событий outbox. Publisher — фаза 2 (NATS JetStream).
 */
@Repository
class R2dbcOutboxRepository(
    private val databaseClient: DatabaseClient
) : OutboxRepository {

    override suspend fun insert(event: OutboxEvent) {
        databaseClient.sql(
            """
            INSERT INTO outbox (id, event_type, payload, created_at)
            VALUES (:id, :event_type, :payload::jsonb, :created_at)
            """.trimIndent()
        )
            .bind("id", event.id)
            .bind("event_type", event.eventType)
            .bind("payload", event.payload)
            .bind("created_at", event.createdAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
