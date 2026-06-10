package chat.privatechat.infrastructure.observability

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.stereotype.Component

/**
 * Прикладные метрики чата для Prometheus (`/actuator/prometheus`).
 */
@Component
class ChatMetrics(private val registry: MeterRegistry) {

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
