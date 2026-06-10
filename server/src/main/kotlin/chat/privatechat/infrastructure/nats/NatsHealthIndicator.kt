package chat.privatechat.infrastructure.nats

import io.nats.client.Connection
import org.springframework.boot.health.contributor.Health
import org.springframework.boot.health.contributor.ReactiveHealthIndicator
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class NatsHealthIndicator(
    private val connection: Connection
) : ReactiveHealthIndicator {

    override fun health(): Mono<Health> =
        Mono.fromCallable {
            if (connection.status == Connection.Status.CONNECTED) {
                Health.up().withDetail("status", connection.status.name).build()
            } else {
                Health.down().withDetail("status", connection.status.name).build()
            }
        }
}
