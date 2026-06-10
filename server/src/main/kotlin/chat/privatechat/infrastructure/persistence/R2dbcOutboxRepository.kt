package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.OutboxEvent
import chat.privatechat.domain.ports.OutboxRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.stereotype.Repository

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

    override suspend fun lockUnpublished(limit: Int): List<OutboxEvent> =
        databaseClient.sql(
            """
            SELECT id, event_type, payload, created_at
            FROM outbox
            WHERE published_at IS NULL
            ORDER BY created_at
            LIMIT :limit
            FOR UPDATE SKIP LOCKED
            """.trimIndent()
        )
            .bind("limit", limit)
            .map { row, _ ->
                OutboxEvent(
                    id = row.get("id", java.util.UUID::class.java)!!,
                    eventType = row.get("event_type", String::class.java)!!,
                    payload = row.get("payload", String::class.java)!!,
                    createdAt = row.get("created_at", java.time.Instant::class.java)!!
                )
            }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun markPublished(id: java.util.UUID, publishedAt: java.time.Instant) {
        databaseClient.sql(
            """
            UPDATE outbox SET published_at = :published_at WHERE id = :id
            """.trimIndent()
        )
            .bind("id", id)
            .bind("published_at", publishedAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
