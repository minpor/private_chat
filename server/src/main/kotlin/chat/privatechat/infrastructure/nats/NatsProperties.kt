package chat.privatechat.infrastructure.nats

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.nats")
data class NatsProperties(
    val url: String = "nats://localhost:4222"
)
