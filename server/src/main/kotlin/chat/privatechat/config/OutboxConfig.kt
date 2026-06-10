package chat.privatechat.config

import chat.privatechat.infrastructure.nats.OutboxProperties
import chat.privatechat.infrastructure.redis.DraftProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(DraftProperties::class, OutboxProperties::class)
class OutboxConfig {

    @Bean
    fun outboxScope(): CoroutineScope {
        val dispatcher = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "outbox-publisher").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        return CoroutineScope(SupervisorJob() + dispatcher)
    }
}
