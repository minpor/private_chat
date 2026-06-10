package chat.privatechat.infrastructure.nats

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.outbox")
data class OutboxProperties(
    val pollIntervalMs: Long = 500,
    val batchSize: Int = 500,
    val publishParallelism: Int = 8,
    val busyBackoffMs: Long = 5
)
