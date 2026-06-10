package chat.privatechat.infrastructure.redis

import org.springframework.data.redis.core.script.RedisScript

object SlidingWindowRateLimitScript {
    val script: RedisScript<Long> = RedisScript.of(
        """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local windowStart = tonumber(ARGV[2])
        local member = ARGV[3]
        local expireSeconds = tonumber(ARGV[4])
        redis.call('ZREMRANGEBYSCORE', key, 0, windowStart)
        redis.call('ZADD', key, now, member)
        local count = redis.call('ZCOUNT', key, windowStart, now)
        redis.call('EXPIRE', key, expireSeconds)
        return count
        """.trimIndent(),
        Long::class.java
    )
}
