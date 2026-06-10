package chat.privatechat.infrastructure.nats

import chat.privatechat.api.ws.WsFrameFactory
import chat.privatechat.api.ws.WsSessionRegistry
import chat.privatechat.domain.ports.ChatRepository
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.PushSubscribeOptions
import io.nats.client.api.AckPolicy
import io.nats.client.api.ConsumerConfiguration
import io.nats.client.api.DeliverPolicy
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.UUID

/**
 * Подписка на NATS JetStream `chat.events` → push `message.new` онлайн-участникам чата.
 */
@Component
@Suppress("TooGenericExceptionCaught")
class NatsChatEventSubscriber(
    private val connection: Connection,
    private val jetStreamSetup: JetStreamSetup,
    private val chatRepository: ChatRepository,
    private val wsSessionRegistry: WsSessionRegistry,
    private val objectMapper: ObjectMapper,
    private val outboxScope: CoroutineScope
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private var dispatcher: Dispatcher? = null

    @PostConstruct
    fun start() {
        jetStreamSetup.ensureStream()
        val subscribeOptions = PushSubscribeOptions.builder()
            .configuration(
                ConsumerConfiguration.builder()
                    .deliverPolicy(DeliverPolicy.New)
                    .ackPolicy(AckPolicy.Explicit)
                    .build()
            )
            .build()
        dispatcher = connection.createDispatcher()
        connection.jetStream().subscribe(
            NatsChatSubjects.EVENTS,
            dispatcher,
            { msg ->
                outboxScope.launch {
                    try {
                        handleEvent(msg)
                    } catch (ex: RuntimeException) {
                        log.warn("Failed to deliver chat event", ex)
                    }
                }
            },
            false,
            subscribeOptions
        )
        log.info("Subscribed to NATS subject {}", NatsChatSubjects.EVENTS)
    }

    @PreDestroy
    fun stop() {
        dispatcher?.let { connection.closeDispatcher(it) }
    }

    private suspend fun handleEvent(msg: Message) {
        val root = objectMapper.readTree(msg.data)
        val eventType = root.get("eventType")?.asText()
            ?: root.get("type")?.asText()
        if (eventType != null && eventType != "message.created") return

        val payload: JsonNode = when {
            root.has("messageId") -> root
            root.has("payload") -> root.get("payload")
            else -> return
        }

        val chatId = UUID.fromString(payload.get("chatId").asText())
        val messageId = UUID.fromString(payload.get("messageId").asText())
        val senderId = UUID.fromString(payload.get("senderId").asText())
        val text = payload.get("text").asText()
        val createdAt = payload.get("createdAt").asText()

        val members = chatRepository.findMembers(chatId).map { it.userId }
        val frame = WsFrameFactory.messageNewFromEvent(messageId, chatId, senderId, text, createdAt)
        wsSessionRegistry.broadcastToUsers(members, frame)
        msg.ack()
    }
}
