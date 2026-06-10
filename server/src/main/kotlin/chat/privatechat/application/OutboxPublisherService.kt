package chat.privatechat.application

import chat.privatechat.domain.OutboxEvent
import chat.privatechat.infrastructure.nats.NatsEventPublisher
import chat.privatechat.infrastructure.observability.ChatMetrics
import chat.privatechat.infrastructure.nats.OutboxProperties
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

        val publishedIds = publishEvents(events)
        if (publishedIds.isNotEmpty()) {
            outboxPublishSupport.markBatch(publishedIds, Instant.now())
            chatMetrics.recordOutboxPublished(publishedIds.size)
            log.debug("Published {} outbox events to NATS", publishedIds.size)
        }

        if (outboxProperties.busyBackoffMs > 0) {
            delay(outboxProperties.busyBackoffMs)
        }
        return true
    }

    private suspend fun publishEvents(events: List<OutboxEvent>): List<java.util.UUID> {
        val parallelism = outboxProperties.publishParallelism.coerceAtLeast(1)
        val semaphore = Semaphore(parallelism)
        return coroutineScope {
            events.map { event ->
                async {
                    semaphore.withPermit {
                        natsEventPublisher.publish(event.payload.toByteArray(Charsets.UTF_8))
                        event.id
                    }
                }
            }.awaitAll()
        }
    }
}
