package chat.privatechat.application

import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.observability.ChatMetrics.RateLimitOperation
import chat.privatechat.infrastructure.redis.DraftProperties
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.domain.Range
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
        val scoreRange = Range.closed(windowStart.toDouble(), now.toDouble())

        val ops = redisTemplate.opsForZSet()
        ops.removeRangeByScore(key, Range.closed(0.0, windowStart.toDouble())).awaitSingleOrNull()
        ops.add(key, member, now.toDouble()).awaitSingleOrNull()
        val count = ops.count(key, scoreRange).awaitSingleOrNull() ?: 0L
        redisTemplate.expire(key, window.plusSeconds(1)).awaitSingleOrNull()

        if (count > limit) {
            chatMetrics.recordRateLimitExceeded(operation)
            throw RateLimitExceededException(retryAfterSeconds = window.seconds.coerceAtLeast(1))
        }
    }
}
