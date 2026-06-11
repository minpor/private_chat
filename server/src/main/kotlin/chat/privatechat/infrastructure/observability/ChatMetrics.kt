package chat.privatechat.infrastructure.observability

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong

/**
 * Прикладные метрики чата для Prometheus (`/actuator/prometheus`).
 */
@Component
class ChatMetrics(private val registry: MeterRegistry) {

    private val outboxBacklog = AtomicLong(0)

    private val natsPublishTimer: Timer = registry.timer("chat.outbox.nats.publish")
    private val outboxLagTimer: Timer = registry.timer("chat.outbox.delivery.lag")
    private val wsDeliveryLagTimer: Timer = registry.timer("chat.delivery.ws.lag")

    init {
        registry.gauge("chat.outbox.backlog", outboxBacklog)
    }

    fun recordMessageAccepted(source: MessageSource) {
        registry.counter("chat.messages.accepted", "source", source.tag).increment()
    }

    fun recordRateLimitExceeded(operation: RateLimitOperation) {
        registry.counter("chat.rate_limit.exceeded", "operation", operation.tag).increment()
    }

    fun recordOutboxPublished(count: Int) {
        if (count > 0) {
            registry.counter("chat.outbox.events.published").increment(count.toDouble())
        }
    }

    fun recordNatsPublishSuccess(publishDuration: Duration, outboxCreatedAt: Instant) {
        natsPublishTimer.record(publishDuration)
        outboxLagTimer.record(Duration.between(outboxCreatedAt, Instant.now()))
    }

    fun recordNatsPublishFailed() {
        registry.counter("chat.outbox.nats.publish.failed").increment()
    }

    fun recordWsDelivered(messageCreatedAt: Instant, recipientCount: Int) {
        registry.counter("chat.delivery.ws.events").increment()
        if (recipientCount > 0) {
            registry.counter("chat.delivery.ws.recipients").increment(recipientCount.toDouble())
        }
        wsDeliveryLagTimer.record(Duration.between(messageCreatedAt, Instant.now()))
    }

    fun recordWsDeliveryFailed() {
        registry.counter("chat.delivery.ws.failed").increment()
    }

    fun setOutboxBacklog(count: Long) {
        outboxBacklog.set(count.coerceAtLeast(0))
    }

    enum class MessageSource(val tag: String) {
        DIRECT("direct"),
        DRAFT("draft")
    }

    enum class RateLimitOperation(val tag: String) {
        PATCH("patch"),
        COMMIT("commit"),
        MESSAGE("message")
    }
}
