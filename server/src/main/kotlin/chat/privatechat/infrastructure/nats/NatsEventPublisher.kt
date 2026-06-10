package chat.privatechat.infrastructure.nats

import io.nats.client.Connection
import io.nats.client.JetStream
import jakarta.annotation.PostConstruct
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

    fun publish(payload: ByteArray) {
        jetStream.publish(NatsChatSubjects.EVENTS, payload)
    }
}
