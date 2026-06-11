package chat.privatechat.api.ws

import chat.privatechat.application.ChatAccessDeniedException
import chat.privatechat.application.DraftNotFoundException
import chat.privatechat.application.DraftRevisionConflictException
import chat.privatechat.application.DraftService
import chat.privatechat.application.MessageCommandService
import chat.privatechat.application.RateLimitExceededException
import chat.privatechat.domain.ports.ChatMemberLookup
import chat.privatechat.infrastructure.jwt.JwtService
import tools.jackson.core.JacksonException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import io.jsonwebtoken.JwtException
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
    private val chatMemberLookup: ChatMemberLookup,
    private val wsSessionRegistry: WsSessionRegistry,
    private val jsonMapper: JsonMapper
) : WebSocketHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun handle(session: WebSocketSession): Mono<Void> {
        val userId = authenticate(session) ?: return session.close()
        wsSessionRegistry.register(userId, session)

        val outbound = session.receive()
            .filter { it.type == WebSocketMessage.Type.TEXT }
            .concatMap { message ->
                val payloadText = message.payloadAsText
                mono { buildResponseFrame(userId, payloadText) }
                    .flatMap { frame ->
                        if (frame == null) {
                            Mono.empty()
                        } else {
                            Mono.just(session.textMessage(toJson(frame)))
                        }
                    }
            }

        return session.send(outbound)
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
    private suspend fun buildResponseFrame(userId: UUID, payloadText: String): Map<String, Any?>? {
        val envelope = try {
            jsonMapper.readValue(payloadText, WsEnvelope::class.java)
        } catch (ex: JacksonException) {
            log.debug("Invalid WS frame: {}", ex.message)
            return WsFrameFactory.error("INVALID_FRAME", "Malformed JSON frame")
        }

        return try {
            when (envelope.type) {
                "draft.start" -> handleDraftStart(userId, envelope.payload)
                "draft.patch" -> handleDraftPatch(userId, envelope.payload)
                "draft.commit" -> handleDraftCommit(userId, envelope.payload)
                "draft.discard" -> {
                    handleDraftDiscard(userId, envelope.payload)
                    null
                }
                else -> WsFrameFactory.error("UNKNOWN_TYPE", "Unknown frame type: ${envelope.type}")
            }
        } catch (ex: DraftNotFoundException) {
            WsFrameFactory.error("DRAFT_GONE", ex.message ?: "Draft not found")
        } catch (ex: DraftRevisionConflictException) {
            WsFrameFactory.error("DRAFT_REVISION_CONFLICT", "Expected revision ${ex.current.revision}")
        } catch (ex: RateLimitExceededException) {
            WsFrameFactory.error("RATE_LIMIT", ex.message ?: "Rate limit exceeded", ex.retryAfterSeconds)
        } catch (ex: ChatAccessDeniedException) {
            WsFrameFactory.error("BAD_REQUEST", ex.message ?: "Access denied")
        } catch (ex: IllegalArgumentException) {
            WsFrameFactory.error("BAD_REQUEST", ex.message ?: "Bad request")
        } catch (ex: RuntimeException) {
            log.warn("WS frame processing failed", ex)
            WsFrameFactory.error("BAD_REQUEST", ex.message ?: "Request failed")
        }
    }

    private suspend fun handleDraftStart(userId: UUID, payload: JsonNode?): Map<String, Any?> {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        val clientSessionId = payload?.get("clientSessionId")?.asText()
        val snapshot = draftService.startDraft(userId, chatId, draftId, clientSessionId)
        broadcastTypingIndicator(chatId, userId, active = true)
        return WsFrameFactory.draftStarted(snapshot.draftId, snapshot.chatId, snapshot.revision)
    }

    private suspend fun handleDraftPatch(userId: UUID, payload: JsonNode?): Map<String, Any?> {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        val revision = payload.requireLong("revision")
        val text = payload?.get("text")?.asText() ?: ""
        val snapshot = draftService.applyPatch(userId, chatId, draftId, revision, text)
        broadcastTypingIndicator(chatId, userId, active = text.isNotBlank())
        return WsFrameFactory.draftPatchAck(snapshot.draftId, snapshot.revision)
    }

    private suspend fun handleDraftCommit(userId: UUID, payload: JsonNode?): Map<String, Any?> {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        val clientMessageId = payload.requireUuid("clientMessageId")
        val expectedRevision = payload?.get("expectedRevision")?.asLong()

        val snapshot = draftService.requireSnapshot(userId, chatId)
        if (snapshot.draftId != draftId) {
            throw DraftNotFoundException(chatId = chatId, draftId = draftId)
        }

        val message = messageCommandService.commitFromDraft(snapshot, clientMessageId, expectedRevision)
        broadcastTypingIndicator(chatId, userId, active = false)
        return WsFrameFactory.messageAccepted(message)
    }

    private suspend fun handleDraftDiscard(userId: UUID, payload: JsonNode?) {
        val chatId = payload.requireUuid("chatId")
        val draftId = payload.requireUuid("draftId")
        draftService.discardDraft(userId, chatId, draftId)
        broadcastTypingIndicator(chatId, userId, active = false)
    }

    private suspend fun broadcastTypingIndicator(chatId: UUID, senderId: UUID, active: Boolean) {
        val frame = WsFrameFactory.typingIndicator(chatId, senderId, active)
        chatMemberLookup.findMemberIds(chatId)
            .filter { it != senderId }
            .forEach { memberId -> wsSessionRegistry.sendToUser(memberId, frame) }
    }

    private fun toJson(frame: Map<String, Any?>): String =
        jsonMapper.writeValueAsString(frame)

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
