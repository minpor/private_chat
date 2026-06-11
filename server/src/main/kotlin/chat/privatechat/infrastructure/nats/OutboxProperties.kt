package chat.privatechat.infrastructure.nats

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.outbox")
data class OutboxProperties(
    val pollIntervalMs: Long = 500,
    val batchSize: Int = 250,
    val publishParallelism: Int = 8,
    val publisherThreads: Int = 0,
    val busyBackoffMs: Long = 5,
    val busyBackoffMinMs: Long = 5,
    val busyBackoffMaxMs: Long = 30,
    val backlogHighWatermark: Int = 100,
    val backlogRefreshIntervalMs: Long = 5000,
    val retentionDays: Int = 7,
    val cleanupIntervalMs: Long = 3600000
) {
    fun resolvedPublisherThreads(): Int =
        if (publisherThreads > 0) {
            publisherThreads
        } else {
            publishParallelism.coerceAtLeast(1)
        }
}
