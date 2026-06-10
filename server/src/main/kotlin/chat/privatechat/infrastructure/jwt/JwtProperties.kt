package chat.privatechat.infrastructure.jwt

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    val secret: String,
    val accessTtlMinutes: Long = 15,
    val refreshTtlDays: Long = 7
)
