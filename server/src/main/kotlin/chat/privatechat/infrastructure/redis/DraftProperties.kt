package chat.privatechat.infrastructure.redis

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.draft")
data class DraftProperties(
    val ttlMinutes: Long = 30,
    val patchRateLimitPerSecond: Int = 20,
    val commitRateLimitPerMinute: Int = 30,
    val messageRateLimitPerMinute: Int = 30
)
