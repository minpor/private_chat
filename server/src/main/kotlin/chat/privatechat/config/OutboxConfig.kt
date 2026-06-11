package chat.privatechat.config

import chat.privatechat.infrastructure.nats.OutboxProperties
import chat.privatechat.infrastructure.redis.ChatMemberCacheProperties
import chat.privatechat.infrastructure.redis.DraftProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors

@Configuration
@EnableConfigurationProperties(
    ChatMemberCacheProperties::class,
    DraftProperties::class,
    OutboxProperties::class
)
class OutboxConfig {

    @Bean
    fun outboxPublisherScope(outboxProperties: OutboxProperties): CoroutineScope {
        val threads = outboxProperties.resolvedPublisherThreads()
        val dispatcher = Executors.newFixedThreadPool(threads) { runnable ->
            Thread(runnable, "outbox-publisher").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    @Bean
    fun natsSubscriberScope(): CoroutineScope {
        val dispatcher = Executors.newFixedThreadPool(NATS_SUBSCRIBER_THREADS) { runnable ->
            Thread(runnable, "nats-subscriber").apply { isDaemon = true }
        }.asCoroutineDispatcher()
        return CoroutineScope(SupervisorJob() + dispatcher)
    }

    companion object {
        private const val NATS_SUBSCRIBER_THREADS = 2
    }
}
