package chat.privatechat.infrastructure.jwt

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.Date
import java.util.UUID
import javax.crypto.SecretKey

/**
 * Выпуск и проверка access JWT (HMAC-SHA256). Refresh — opaque token в PostgreSQL.
 *
 * Успешно проверенные access-токены кэшируются до `exp` ([JwtAccessTokenCache]).
 */
@Service
class JwtService(
    private val properties: JwtProperties,
    private val accessTokenCache: JwtAccessTokenCache
) {
    private val secretKey: SecretKey by lazy {
        Keys.hmacShaKeyFor(properties.secret.toByteArray(Charsets.UTF_8))
    }

    fun createAccessToken(userId: UUID, username: String): String {
        val now = Instant.now()
        val expiresAt = now.plusSeconds(properties.accessTtlMinutes * 60)
        return Jwts.builder()
            .subject(userId.toString())
            .claim(CLAIM_USERNAME, username)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(secretKey)
            .compact()
    }

    fun parseAccessToken(token: String): AccessTokenClaims {
        accessTokenCache.get(token)?.let { return it }

        val claims = Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .payload
        val expiresAt = claims.expiration.toInstant()
        val parsed = AccessTokenClaims(
            userId = UUID.fromString(claims.subject),
            username = claims.get(CLAIM_USERNAME, String::class.java)
        )
        accessTokenCache.put(token, parsed, expiresAt)
        return parsed
    }

    fun refreshExpiresAt(): Instant =
        Instant.now().plusSeconds(properties.refreshTtlDays * 24 * 60 * 60)

    private companion object {
        const val CLAIM_USERNAME = "username"
    }
}

data class AccessTokenClaims(
    val userId: UUID,
    val username: String
)
