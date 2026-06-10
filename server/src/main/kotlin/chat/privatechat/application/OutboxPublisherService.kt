package chat.privatechat.application

import chat.privatechat.domain.ports.OutboxRepository
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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

/**
 * Фоновый poll transactional outbox → NATS JetStream.
 */
@Service
@Suppress("TooGenericExceptionCaught")
class OutboxPublisherService(
    private val outboxRepository: OutboxRepository,
    private val natsEventPublisher: NatsEventPublisher,
    private val outboxProperties: OutboxProperties,
    private val outboxScope: CoroutineScope,
    private val chatMetrics: ChatMetrics
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var publisherJob: Job? = null

    @PostConstruct
    fun start() {
        publisherJob = outboxScope.launch {
            while (isActive) {
                try {
                    publishBatch()
                } catch (ex: RuntimeException) {
                    log.warn("Outbox publish batch failed", ex)
                }
                delay(outboxProperties.pollIntervalMs)
            }
        }
    }

    @PreDestroy
    fun stop() {
        publisherJob?.cancel()
    }

    @Transactional
    suspend fun publishBatch() {
        val events = outboxRepository.lockUnpublished(outboxProperties.batchSize)
        if (events.isEmpty()) return

        val publishedAt = Instant.now()
        for (event in events) {
            natsEventPublisher.publish(event.payload.toByteArray(Charsets.UTF_8))
            outboxRepository.markPublished(event.id, publishedAt)
        }
        chatMetrics.recordOutboxPublished(events.size)
        log.debug("Published {} outbox events to NATS", events.size)
    }
}
