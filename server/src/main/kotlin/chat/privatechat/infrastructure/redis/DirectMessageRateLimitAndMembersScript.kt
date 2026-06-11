package chat.privatechat.infrastructure.redis

import org.springframework.data.redis.core.script.RedisScript

/**
 * Atomic fixed-window rate limit + chat member cache GET in one Redis round-trip.
 */
object DirectMessageRateLimitAndMembersScript {
    val script: RedisScript<List<*>> = RedisScript.of(
        """
        local rateKey = KEYS[1]
        local membersKey = KEYS[2]
        local expireSeconds = tonumber(ARGV[1])
        local count = redis.call('INCR', rateKey)
        if count == 1 then
            redis.call('EXPIRE', rateKey, expireSeconds)
        end
        local members = redis.call('GET', membersKey)
        if members == false then
            members = ''
        end
        return {count, members}
        """.trimIndent(),
        List::class.java
    )
}
