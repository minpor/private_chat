package chat.privatechat.infrastructure.redis

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.chat-member-cache")
data class ChatMemberCacheProperties(
    val ttlMinutes: Long = 1440
)
