package chat.privatechat.infrastructure.redis

import org.springframework.data.redis.core.script.RedisScript

/**
 * Fixed-window counter: [INCR] + [EXPIRE] on first hit in one atomic Lua script.
 * Cheaper than sorted-set sliding window for high per-minute limits.
 */
object FixedWindowIncrRateLimitScript {
    val script: RedisScript<Long> = RedisScript.of(
        """
        local key = KEYS[1]
        local expireSeconds = tonumber(ARGV[1])
        local count = redis.call('INCR', key)
        if count == 1 then
            redis.call('EXPIRE', key, expireSeconds)
        end
        return count
        """.trimIndent(),
        Long::class.java
    )
}
