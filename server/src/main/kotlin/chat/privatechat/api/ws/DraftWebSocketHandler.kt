package chat.privatechat.api.ws

import chat.privatechat.application.DraftNotFoundException
import chat.privatechat.application.DraftRevisionConflictException
import chat.privatechat.application.DraftService
import chat.privatechat.application.MessageCommandService
import chat.privatechat.application.RateLimitExceededException
import chat.privatechat.infrastructure.jwt.JwtService
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.jsonwebtoken.JwtException
import kotlinx.coroutines.reactor.awaitSingleOrNull
import kotlinx.coroutines.reactor.mono
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketMessage
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.util.UUID

/**
 * WebSocket draft-sync: `draft.start`, `draft.patch`, `draft.commit`, `draft.discard`.
 *
 * Аутентификация: `?token=<JWT>` в query string.
 */
@Component
@Suppress("TooManyFunctions")
class DraftWebSocketHandler(
    private val jwtService: JwtService,
    private val draftService: DraftService,
    private val messageCommandService: MessageCommandService,
    private val wsSessionRegistry: WsSessionRegistry,
    private val objectMapper: ObjectMapper
) : WebSocketHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val userId = authenticate(session) ?: return session.close()
        wsSessionRegistry.register(userId, session)
        return session.receive()
            .flatMap { message -> mono { dispatchFrame(userId, session, message) } }
            .then()
            .doFinally { wsSessionRegistry.unregister(userId, session) }
    }

    private fun authenticate(session: WebSocketSession): UUID? {
        val token = extractToken(session.handshakeInfo.uri.query) ?: return null
        return try {
            jwtService.parseAccessToken(token).userId
        } catch (ex: JwtException) {
            log.debug("WS auth failed: {}", ex.message)
            null
        }
    }

    private fun extractToken(query: String?): String? {
        if (query == null) return null
        return query.split("&")
            .firstNotNullOfOrNull { part ->
                val kv = part.split("=", limit = 2)
                if (kv.size == 2 && kv[0] == "token" && kv[1].isNotEmpty()) kv[1] else null
            }
    }

    @Suppress("CyclomaticComplexMethod")
    private suspend fun dispatchFrame(userId: UUID, session: WebSocketSession, message: WebSocketMessage) {
        if (!message.type.equals(WebSocketMessage.Type.TEXT)) return

        val envelope = try {
            objectMapper.readValue(message.payloadAsText, WsEnvelope::class.java)
        } catch (ex: JsonProcessingException) {
            log.debug("Invalid WS frame: {}", ex.message)
            send(session, WsFrameFactory.error("INVALID_FRAME", "Malformed JSON frame"))
            return
        }

        try {
            when (envelope.type) {
                "draft.start" -> handleDraftStart(userId, session, envelope.payload)
                "draft.patch" -> handleDraftPatch(userId, session, envelope.payload)
                "draft.commit" -> handleDraftCommit(userId, session, envelope.payload)
                "draft.discard" -> handleDraftDiscard(userId, envelope.payload)
                else -> send(session, WsFrameFactory.error("UNKNOWN_TYPE", "Unknown frame type: ${envelope.type}"))
            }
        } catch (ex: DraftNotFoundException) {
            send(session, WsFrameFactory.error("DRAFT_GONE", ex.message ?: "Draft not found"))
        } catch (ex: DraftRevisionConflictException) {
            send(session, WsFrameFactory.error("DRAFT_REVISION_CONFLICT", "Expected revision ${ex.current.revision}"))
        } catch (ex: RateLimitExceededException) {
            send(session, WsFrameFactory.error("RATE_LIMIT", ex.message ?: "Rate limit exceeded", ex.retryAfterSeconds))
        } catch (ex: IllegalArgumentException) {
            send(session, WsFrameFactory.error("BAD_REQUEST", ex.message ?: "Bad request"))
        }
    }

    private suspend fun handleDraftStart(userId: UUID, session: WebSocketSession, payload: JsonNode?) {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        val clientSessionId = payload?.get("clientSessionId")?.asText()
        val snapshot = draftService.startDraft(userId, chatId, draftId, clientSessionId)
        send(session, WsFrameFactory.draftStarted(snapshot.draftId, snapshot.chatId, snapshot.revision))
    }

    private suspend fun handleDraftPatch(userId: UUID, session: WebSocketSession, payload: JsonNode?) {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        val revision = payload.requireLong("revision")
        val text = payload?.get("text")?.asText() ?: ""
        val snapshot = draftService.applyPatch(userId, chatId, draftId, revision, text)
        send(session, WsFrameFactory.draftPatchAck(snapshot.draftId, snapshot.revision))
    }

    private suspend fun handleDraftCommit(userId: UUID, session: WebSocketSession, payload: JsonNode?) {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        val clientMessageId = payload.requireUuid("clientMessageId")
        val expectedRevision = payload?.get("expectedRevision")?.asLong()

        val snapshot = draftService.requireSnapshot(userId, chatId)
        if (snapshot.draftId != draftId) {
            throw DraftNotFoundException(chatId = chatId, draftId = draftId)
        }

        val message = messageCommandService.commitFromDraft(snapshot, clientMessageId, expectedRevision)
        send(session, WsFrameFactory.messageAccepted(message))
    }

    private suspend fun handleDraftDiscard(userId: UUID, payload: JsonNode?) {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        draftService.discardDraft(userId, chatId, draftId)
    }

    private suspend fun send(session: WebSocketSession, frame: Map<String, Any?>) {
        val json = objectMapper.writeValueAsString(frame)
        session.send(Mono.just(session.textMessage(json))).awaitSingleOrNull()
    }

    private fun JsonNode?.requireUuid(field: String): UUID {
        val text = this?.get(field)?.asText()
            ?: throw IllegalArgumentException("Missing field: $field")
        return UUID.fromString(text)
    }

    private fun JsonNode?.requireLong(field: String): Long {
        val node = this?.get(field) ?: throw IllegalArgumentException("Missing field: $field")
        return node.asLong()
    }
}
