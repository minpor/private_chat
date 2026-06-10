package chat.privatechat.infrastructure.nats

import io.nats.client.Connection
import io.nats.client.JetStreamApiException
import io.nats.client.api.RetentionPolicy
import io.nats.client.api.StorageType
import io.nats.client.api.StreamConfiguration
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class JetStreamSetup(
    private val connection: Connection
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun ensureStream() {
        val management = connection.jetStreamManagement()
        try {
            management.getStreamInfo(NatsChatSubjects.STREAM_NAME)
        } catch (_: JetStreamApiException) {
            val config = StreamConfiguration.builder()
                .name(NatsChatSubjects.STREAM_NAME)
                .subjects(NatsChatSubjects.EVENTS)
                .storageType(StorageType.File)
                .retentionPolicy(RetentionPolicy.Limits)
                .build()
            management.addStream(config)
            log.info("Created JetStream stream {}", NatsChatSubjects.STREAM_NAME)
        }
    }
}
