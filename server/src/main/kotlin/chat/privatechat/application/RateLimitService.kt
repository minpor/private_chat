package chat.privatechat.application

import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.observability.ChatMetrics.RateLimitOperation
import chat.privatechat.infrastructure.redis.DraftProperties
import chat.privatechat.infrastructure.redis.SlidingWindowRateLimitScript
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * Sliding-window rate limits в Redis (patch / commit / direct message).
 */
@Service
class RateLimitService(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val draftProperties: DraftProperties,
    private val chatMetrics: ChatMetrics
) {
    suspend fun checkPatchLimit(userId: UUID, chatId: UUID) {
        val key = "ratelimit:patch:$userId:$chatId"
        checkSlidingWindow(
            key = key,
            window = Duration.ofSeconds(1),
            limit = draftProperties.patchRateLimitPerSecond,
            operation = RateLimitOperation.PATCH
        )
    }

    suspend fun checkCommitLimit(userId: UUID) {
        val key = "ratelimit:commit:$userId"
        checkSlidingWindow(
            key = key,
            window = Duration.ofMinutes(1),
            limit = draftProperties.commitRateLimitPerMinute,
            operation = RateLimitOperation.COMMIT
        )
    }

    suspend fun checkDirectMessageLimit(userId: UUID) {
        val key = "ratelimit:message:$userId"
        checkSlidingWindow(
            key = key,
            window = Duration.ofMinutes(1),
            limit = draftProperties.messageRateLimitPerMinute,
            operation = RateLimitOperation.MESSAGE
        )
    }

    private suspend fun checkSlidingWindow(
        key: String,
        window: Duration,
        limit: Int,
        operation: RateLimitOperation
    ) {
        val now = System.currentTimeMillis()
        val windowStart = now - window.toMillis()
        val member = "$now:${UUID.randomUUID()}"
        val expireSeconds = window.seconds.coerceAtLeast(1) + 1

        val count = redisTemplate.execute(
            SlidingWindowRateLimitScript.script,
            listOf(key),
            listOf(
                now.toString(),
                windowStart.toString(),
                member,
                expireSeconds.toString()
            )
        )
            .collectList()
            .awaitSingle()
            .firstOrNull() ?: 0L

        if (count > limit) {
            chatMetrics.recordRateLimitExceeded(operation)
            throw RateLimitExceededException(retryAfterSeconds = window.seconds.coerceAtLeast(1))
        }
    }
}
