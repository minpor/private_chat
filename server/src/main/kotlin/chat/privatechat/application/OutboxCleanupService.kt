package chat.privatechat.application

import chat.privatechat.domain.ports.OutboxRepository
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
import java.time.temporal.ChronoUnit

@Service
@Suppress("TooGenericExceptionCaught")
class OutboxCleanupService(
    private val outboxRepository: OutboxRepository,
    private val outboxProperties: OutboxProperties,
    @Qualifier("outboxPublisherScope") private val outboxPublisherScope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var cleanupJob: Job? = null

    @PostConstruct
    fun start() {
        if (outboxProperties.retentionDays <= 0) {
            log.info("Outbox cleanup disabled (retention-days <= 0)")
            return
        }
        cleanupJob = outboxPublisherScope.launch {
            while (isActive) {
                try {
                    delay(outboxProperties.cleanupIntervalMs)
                    val cutoff = Instant.now().minus(outboxProperties.retentionDays.toLong(), ChronoUnit.DAYS)
                    val deleted = outboxRepository.deletePublishedBefore(cutoff)
                    if (deleted > 0) {
                        log.info("Deleted {} published outbox rows older than {}", deleted, cutoff)
                    }
                } catch (ex: RuntimeException) {
                    log.warn("Outbox cleanup failed", ex)
                }
            }
        }
    }

    @PreDestroy
    fun stop() {
        cleanupJob?.cancel()
    }
}
