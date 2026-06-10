package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.ports.RefreshTokenRecord
import chat.privatechat.domain.ports.RefreshTokenRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.stereotype.Repository
import java.time.Instant
import java.util.UUID

@Repository
class R2dbcRefreshTokenRepository(
    private val databaseClient: DatabaseClient
) : RefreshTokenRepository {

    override suspend fun insert(id: UUID, userId: UUID, tokenHash: String, expiresAt: Instant) {
        databaseClient.sql(
            """
            INSERT INTO refresh_tokens (id, user_id, token_hash, expires_at)
            VALUES (:id, :user_id, :token_hash, :expires_at)
            """.trimIndent()
        )
            .bind("id", id)
            .bind("user_id", userId)
            .bind("token_hash", tokenHash)
            .bind("expires_at", expiresAt)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun findActiveByTokenHash(tokenHash: String): RefreshTokenRecord? =
        databaseClient.sql(
            """
            SELECT id, user_id, token_hash, expires_at
            FROM refresh_tokens
            WHERE token_hash = :token_hash
              AND revoked_at IS NULL
              AND expires_at > now()
            """.trimIndent()
        )
            .bind("token_hash", tokenHash)
            .map { row, _ ->
                RefreshTokenRecord(
                    id = row.get("id", UUID::class.java)!!,
                    userId = row.get("user_id", UUID::class.java)!!,
                    tokenHash = row.get("token_hash", String::class.java)!!,
                    expiresAt = row.get("expires_at", Instant::class.java)!!
                )
            }
            .awaitOneOrNull()

    override suspend fun revoke(id: UUID) {
        databaseClient.sql(
            """
            UPDATE refresh_tokens
            SET revoked_at = now()
            WHERE id = :id
            """.trimIndent()
        )
            .bind("id", id)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }

    override suspend fun revokeAllForUser(userId: UUID) {
        databaseClient.sql(
            """
            UPDATE refresh_tokens
            SET revoked_at = now()
            WHERE user_id = :user_id AND revoked_at IS NULL
            """.trimIndent()
        )
            .bind("user_id", userId)
            .fetch()
            .rowsUpdated()
            .awaitSingle()
    }
}
