package chat.privatechat.infrastructure.nats

import io.nats.client.Connection
import io.nats.client.JetStream
import io.nats.client.api.PublishAck
import jakarta.annotation.PostConstruct
import kotlinx.coroutines.future.await
import org.springframework.stereotype.Component

@Component
class NatsEventPublisher(
    private val connection: Connection,
    private val jetStreamSetup: JetStreamSetup
) {
    private lateinit var jetStream: JetStream

    @PostConstruct
    fun init() {
        jetStreamSetup.ensureStream()
        jetStream = connection.jetStream()
    }

    suspend fun publish(payload: ByteArray): PublishAck =
        jetStream.publishAsync(NatsChatSubjects.EVENTS, payload).await()
}
