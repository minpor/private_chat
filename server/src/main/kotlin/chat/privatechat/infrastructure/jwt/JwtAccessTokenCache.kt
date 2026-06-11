package chat.privatechat.infrastructure.jwt

import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Кэш уже проверенных access JWT до момента [Instant] истечения из claim `exp`.
 *
 * Ключ включает префикс от [JwtProperties.secret], чтобы при ротации секрета
 * старые записи не использовались без повторной верификации подписи.
 *
 * Реализация на [ConcurrentHashMap] (без Caffeine) — совместима с GraalVM Native Image.
 */
@Component
class JwtAccessTokenCache(
    properties: JwtProperties
) {
    private val enabled = properties.accessTokenCacheEnabled
    private val maxSize = properties.accessTokenCacheMaxSize
    private val keyPrefix = secretKeyPrefix(properties.secret)
    private val cache = ConcurrentHashMap<String, CachedAccessToken>()

    fun get(token: String): AccessTokenClaims? {
        if (!enabled) return null
        val key = cacheKey(token)
        val entry = cache[key] ?: return null
        if (Instant.now().isAfter(entry.expiresAt)) {
            cache.remove(key)
            return null
        }
        return entry.claims
    }

    fun put(token: String, claims: AccessTokenClaims, expiresAt: Instant) {
        if (!enabled) return
        evictExpired()
        evictOverflow()
        cache[cacheKey(token)] = CachedAccessToken(claims, expiresAt)
    }

    private fun evictExpired() {
        val now = Instant.now()
        cache.entries.removeIf { (_, value) -> now.isAfter(value.expiresAt) }
    }

    private fun evictOverflow() {
        while (cache.size >= maxSize) {
            val oldest = cache.entries.minByOrNull { it.value.expiresAt }?.key ?: break
            cache.remove(oldest)
        }
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
