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
                val payload = row.get("payload", String::class.java)!!
                OutboxEvent(
                    id = row.get("id", java.util.UUID::class.java)!!,
                    eventType = row.get("event_type", String::class.java)!!,
                    payload = payload,
                    createdAt = row.get("created_at", java.time.Instant::class.java)!!,
                    natsPayload = payload.toByteArray(Charsets.UTF_8)
                )
            }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun markPublishedBatch(ids: List<java.util.UUID>, publishedAt: java.time.Instant) {
        if (ids.isEmpty()) return
        databaseClient.sql(
            """
            UPDATE outbox SET published_at = :published_at WHERE id = ANY(:ids)
            """.trimIndent()
        )
            .bind("published_at", publishedAt)
            .bind("ids", ids.toTypedArray())
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun countUnpublished(): Long =
        databaseClient.sql(
            """
            SELECT COUNT(*) AS cnt FROM outbox WHERE published_at IS NULL
            """.trimIndent()
        )
            .map { row, _ -> row.get("cnt", java.lang.Long::class.java)!!.toLong() }
            .one()
            .awaitSingle()

    override suspend fun deletePublishedBefore(cutoff: java.time.Instant): Long =
        databaseClient.sql(
            """
            DELETE FROM outbox
            WHERE published_at IS NOT NULL AND published_at < :cutoff
            """.trimIndent()
        )
            .bind("cutoff", cutoff)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
}
