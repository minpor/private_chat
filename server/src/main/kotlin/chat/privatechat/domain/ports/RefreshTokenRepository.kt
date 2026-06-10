package chat.privatechat.domain.ports

import java.time.Instant
import java.util.UUID

/**
 * Хранение refresh-токенов с rotation (хэш в БД, не plaintext).
 */
interface RefreshTokenRepository {
    suspend fun insert(id: UUID, userId: UUID, tokenHash: String, expiresAt: Instant)

    suspend fun findActiveByTokenHash(tokenHash: String): RefreshTokenRecord?

    suspend fun revoke(id: UUID)

    suspend fun revokeAllForUser(userId: UUID)
}

data class RefreshTokenRecord(
    val id: UUID,
    val userId: UUID,
    val tokenHash: String,
    val expiresAt: Instant
)
