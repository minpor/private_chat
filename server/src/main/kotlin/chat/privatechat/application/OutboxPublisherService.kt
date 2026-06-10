package chat.privatechat.application

import chat.privatechat.infrastructure.nats.NatsEventPublisher
import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.nats.OutboxProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Фоновый poll transactional outbox → NATS JetStream.
 */
@Service
@Suppress("TooGenericExceptionCaught")
class OutboxPublisherService(
    private val outboxPublishSupport: OutboxPublishSupport,
    private val natsEventPublisher: NatsEventPublisher,
    private val outboxProperties: OutboxProperties,
    @Qualifier("outboxPublisherScope") private val outboxPublisherScope: CoroutineScope,
    private val chatMetrics: ChatMetrics
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var publisherJob: Job? = null

    @PostConstruct
    fun start() {
        publisherJob = outboxPublisherScope.launch {
            while (isActive) {
                try {
                    val hadWork = publishBatch()
                    if (!hadWork) {
                        delay(outboxProperties.pollIntervalMs)
                    }
                } catch (ex: RuntimeException) {
                    log.warn("Outbox publish batch failed", ex)
                    delay(outboxProperties.pollIntervalMs)
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        publisherJob?.cancel()
    }

    private suspend fun publishBatch(): Boolean {
        val events = outboxPublishSupport.lockBatch(outboxProperties.batchSize)
        if (events.isEmpty()) return false

        for (event in events) {
            natsEventPublisher.publish(event.payload.toByteArray(Charsets.UTF_8))
        }
        outboxPublishSupport.markBatch(events.map { it.id }, Instant.now())
        chatMetrics.recordOutboxPublished(events.size)
        log.debug("Published {} outbox events to NATS", events.size)

        delay(BUSY_BACKOFF_MS)
        return true
    }

    companion object {
        private const val BUSY_BACKOFF_MS = 50L
    }
}
