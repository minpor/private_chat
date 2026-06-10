package chat.privatechat.infrastructure.nats

import io.nats.client.Connection
import io.nats.client.Nats
import io.nats.client.Options
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration

/**
 * NATS-клиент (jnats). JetStream publisher — фаза 2.
 */
@Configuration
class NatsConfig(
    private val properties: NatsProperties
) {
    @Bean(destroyMethod = "close")
    fun natsConnection(): Connection =
        Nats.connect(
            Options.builder()
                .server(properties.url)
                .connectionTimeout(Duration.ofSeconds(5))
                .build()
        )
}
