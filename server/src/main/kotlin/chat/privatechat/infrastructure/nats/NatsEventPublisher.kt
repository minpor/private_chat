package chat.privatechat.infrastructure.nats

import io.nats.client.Connection
import org.springframework.stereotype.Component

@Component
class NatsEventPublisher(
    private val connection: Connection,
    private val jetStreamSetup: JetStreamSetup
) {
    fun publish(payload: ByteArray) {
        jetStreamSetup.ensureStream()
        connection.jetStream().publish(NatsChatSubjects.EVENTS, payload)
    }
}
