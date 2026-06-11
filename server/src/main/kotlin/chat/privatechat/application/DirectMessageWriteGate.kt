package chat.privatechat.application

import chat.privatechat.domain.ports.ChatMemberLookup
import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.observability.ChatMetrics.RateLimitOperation
import chat.privatechat.infrastructure.redis.ChatMemberRedisKeys
import chat.privatechat.infrastructure.redis.DirectMessageRateLimitAndMembersScript
import chat.privatechat.infrastructure.redis.DraftProperties
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.UUID

/**
 * Combined Redis prefetch for direct POST /messages: rate limit + member cache.
 */
@Service
class DirectMessageWriteGate(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val draftProperties: DraftProperties,
    private val chatMemberLookup: ChatMemberLookup,
    private val rateLimitService: RateLimitService,
    private val chatMetrics: ChatMetrics
) {
    suspend fun checkLimitAndFindMembers(userId: UUID, chatId: UUID): List<UUID> {
        val limit = draftProperties.messageRateLimitPerMinute
        if (limit <= draftProperties.messageRateLimitFixedWindowThreshold) {
            rateLimitService.checkDirectMessageLimit(userId)
            return chatMemberLookup.findMemberIds(chatId)
        }

        val window = Duration.ofMinutes(1)
        val rateKey = "ratelimit:message:$userId"
        val membersKey = ChatMemberRedisKeys.membersKey(chatId)
        val expireSeconds = window.seconds.coerceAtLeast(1) + 1

        val result = redisTemplate.execute(
            DirectMessageRateLimitAndMembersScript.script,
            listOf(rateKey, membersKey),
            listOf(expireSeconds.toString())
        )
            .collectList()
            .awaitSingle()
            .firstOrNull() ?: emptyList<Any>()

        val count = (result.getOrNull(0) as? Number)?.toLong() ?: 0L
        if (count > limit) {
            chatMetrics.recordRateLimitExceeded(RateLimitOperation.MESSAGE)
            throw RateLimitExceededException(retryAfterSeconds = window.seconds.coerceAtLeast(1))
        }

        val cached = redisStringValue(result.getOrNull(1))
        if (!cached.isNullOrEmpty()) {
            return decodeMemberIds(cached)
        }

        return chatMemberLookup.findMemberIds(chatId)
    }

    private fun redisStringValue(raw: Any?): String? = when (raw) {
        null -> null
        is String -> raw
        is ByteArray -> raw.toString(Charsets.UTF_8)
        else -> raw.toString()
    }

    private fun decodeMemberIds(value: String): List<UUID> =
        value.split(MEMBER_ID_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { UUID.fromString(it) }

    private companion object {
        const val MEMBER_ID_SEPARATOR = ","
    }
}
