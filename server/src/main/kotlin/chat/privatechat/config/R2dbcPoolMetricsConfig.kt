package chat.privatechat.config

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.spi.ConnectionFactory
import jakarta.annotation.PostConstruct
import org.springframework.context.annotation.Configuration

/**
 * Exposes r2dbc-pool metrics for tuning [R2DBC_POOL_MAX_SIZE] under load.
 */
@Configuration
class R2dbcPoolMetricsConfig(
    private val connectionFactory: ConnectionFactory,
    private val meterRegistry: MeterRegistry
) {
    @PostConstruct
    fun registerPoolMetrics() {
        val pool = connectionFactory as? ConnectionPool ?: return
        val metrics = pool.metrics.orElse(null) ?: return
        val prefix = "r2dbc.pool"

        Gauge.builder("$prefix.acquired") { metrics.acquiredSize().toDouble() }
            .description("R2DBC connections currently acquired")
            .register(meterRegistry)
        Gauge.builder("$prefix.allocated") { metrics.allocatedSize().toDouble() }
            .description("R2DBC connections allocated in the pool")
            .register(meterRegistry)
        Gauge.builder("$prefix.idle") { metrics.idleSize().toDouble() }
            .description("R2DBC idle connections in the pool")
            .register(meterRegistry)
        Gauge.builder("$prefix.pending.acquire") { metrics.pendingAcquireSize().toDouble() }
            .description("R2DBC threads waiting for a connection")
            .register(meterRegistry)
        Gauge.builder("$prefix.max.allocated") { metrics.maxAllocatedSize.toDouble() }
            .description("R2DBC pool max allocated size")
            .register(meterRegistry)
        Gauge.builder("$prefix.max.pending.acquire") { metrics.maxPendingAcquireSize.toDouble() }
            .description("R2DBC peak pending acquire count")
            .register(meterRegistry)
    }
}
