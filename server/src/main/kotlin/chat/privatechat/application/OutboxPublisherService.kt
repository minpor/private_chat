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
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

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
    private val estimatedBacklog = AtomicLong(0)
    private var lastBacklogRefreshAtMs = 0L

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
        if (events.isEmpty()) {
            estimatedBacklog.set(0)
            chatMetrics.setOutboxBacklog(0)
            refreshBacklogFromDatabase(force = true)
            return false
        }

        val publishedIds = publishEvents(events)
        if (publishedIds.isNotEmpty()) {
            outboxPublishSupport.markBatch(publishedIds, Instant.now())
            chatMetrics.recordOutboxPublished(publishedIds.size)
            estimatedBacklog.addAndGet(-publishedIds.size.toLong())
            chatMetrics.setOutboxBacklog(estimatedBacklog.get().coerceAtLeast(0))
            log.debug("Published {} outbox events to NATS", publishedIds.size)
        }

        refreshBacklogFromDatabase(force = false)
        delay(resolveBusyBackoffMs(events.size == outboxProperties.batchSize))
        return true
    }

    private suspend fun refreshBacklogFromDatabase(force: Boolean) {
        val now = System.currentTimeMillis()
        if (!force && now - lastBacklogRefreshAtMs < outboxProperties.backlogRefreshIntervalMs) {
            return
        }
        val count = outboxPublishSupport.countUnpublished()
        estimatedBacklog.set(count)
        chatMetrics.setOutboxBacklog(count)
        lastBacklogRefreshAtMs = now
    }

    private fun resolveBusyBackoffMs(batchWasFull: Boolean): Long {
        if (batchWasFull) {
            return outboxProperties.busyBackoffMinMs.coerceAtLeast(0)
        }
        val backlog = estimatedBacklog.get()
        return if (backlog > outboxProperties.backlogHighWatermark) {
            outboxProperties.busyBackoffMinMs.coerceAtLeast(0)
        } else {
            outboxProperties.busyBackoffMaxMs.coerceAtLeast(0)
        }
    }

    private suspend fun publishEvents(events: List<OutboxEvent>): List<java.util.UUID> {
        val parallelism = outboxProperties.publishParallelism.coerceAtLeast(1)
        val semaphore = Semaphore(parallelism)
        return coroutineScope {
            events.map { event ->
                async {
                    semaphore.withPermit {
                        publishOne(event)
                    }
                }
            }.awaitAll().filterNotNull()
        }
    }

    private suspend fun publishOne(event: OutboxEvent): java.util.UUID? {
        val publishStarted = System.nanoTime()
        return try {
            natsEventPublisher.publish(event.bytesForNats())
            val publishDuration = Duration.ofNanos(System.nanoTime() - publishStarted)
            chatMetrics.recordNatsPublishSuccess(publishDuration, event.createdAt)
            event.id
        } catch (ex: Exception) {
            chatMetrics.recordNatsPublishFailed()
            log.warn("Failed to publish outbox event {}", event.id, ex)
            null
        }
    }
}
