package chat.privatechat.infrastructure.jwt

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Expiry
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant

/**
 * Кэш уже проверенных access JWT до момента [Instant] истечения из claim `exp`.
 *
 * Ключ включает префикс от [JwtProperties.secret], чтобы при ротации секрета
 * старые записи не использовались без повторной верификации подписи.
 */
@Component
class JwtAccessTokenCache(
    properties: JwtProperties
) {
    private val enabled = properties.accessTokenCacheEnabled
    private val keyPrefix = secretKeyPrefix(properties.secret)

    private val cache = Caffeine.newBuilder()
        .maximumSize(properties.accessTokenCacheMaxSize)
        .expireAfter(
            object : Expiry<String, CachedAccessToken> {
                override fun expireAfterCreate(
                    key: String,
                    value: CachedAccessToken,
                    currentTime: Long
                ): Long =
                    Duration.between(Instant.now(), value.expiresAt).toNanos().coerceAtLeast(0)

                override fun expireAfterUpdate(
                    key: String,
                    value: CachedAccessToken,
                    currentTime: Long,
                    currentDuration: Long
                ): Long = currentDuration

                override fun expireAfterRead(
                    key: String,
                    value: CachedAccessToken,
                    currentTime: Long,
                    currentDuration: Long
                ): Long = currentDuration
            }
        )
        .build<String, CachedAccessToken>()

    fun get(token: String): AccessTokenClaims? {
        if (!enabled) return null
        return cache.getIfPresent(cacheKey(token))?.claims
    }

    fun put(token: String, claims: AccessTokenClaims, expiresAt: Instant) {
        if (!enabled) return
        cache.put(cacheKey(token), CachedAccessToken(claims, expiresAt))
    }

    private fun cacheKey(token: String): String =
        "$keyPrefix:${sha256Hex(token)}"

    private data class CachedAccessToken(
        val claims: AccessTokenClaims,
        val expiresAt: Instant
    )

    private companion object {
        fun secretKeyPrefix(secret: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(secret.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
                .take(16)

        fun sha256Hex(value: String): String =
            MessageDigest.getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString("") { byte -> "%02x".format(byte) }
    }
}
