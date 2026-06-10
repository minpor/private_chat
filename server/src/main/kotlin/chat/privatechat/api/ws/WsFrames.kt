package chat.privatechat.api.ws

import chat.privatechat.domain.Message
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.util.UUID

data class WsEnvelope(
    val type: String,
    val payload: JsonNode? = null
)

data class WsErrorPayload(
    val code: String,
    val message: String,
    val retryAfterSeconds: Long? = null
)

data class DraftStartedPayload(
    val draftId: String,
    val chatId: String,
    val revision: Long
)

data class DraftPatchAckPayload(
    val draftId: String,
    val revision: Long,
    val serverTime: String
)

data class MessageAcceptedPayload(
    val id: String,
    val chatId: String,
    val clientMessageId: String,
    val status: String,
    val createdAt: String
)

data class MessageNewPayload(
    val id: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val createdAt: String
)

object WsFrameFactory {
    fun draftStarted(draftId: UUID, chatId: UUID, revision: Long): Map<String, Any?> =
        mapOf(
            "type" to "draft.started",
            "payload" to DraftStartedPayload(
                draftId = draftId.toString(),
                chatId = chatId.toString(),
                revision = revision
            )
        )

    fun draftPatchAck(draftId: UUID, revision: Long): Map<String, Any?> =
        mapOf(
            "type" to "draft.patch.ack",
            "payload" to DraftPatchAckPayload(
                draftId = draftId.toString(),
                revision = revision,
                serverTime = Instant.now().toString()
            )
        )

    fun messageAccepted(message: Message): Map<String, Any?> =
        mapOf(
            "type" to "message.accepted",
            "payload" to MessageAcceptedPayload(
                id = message.id.toString(),
                chatId = message.chatId.toString(),
                clientMessageId = message.clientMessageId.toString(),
                status = message.status.name.lowercase(),
                createdAt = message.createdAt.toString()
            )
        )

    fun messageNew(message: Message): Map<String, Any?> =
        mapOf(
            "type" to "message.new",
            "payload" to MessageNewPayload(
                id = message.id.toString(),
                chatId = message.chatId.toString(),
                senderId = message.senderId.toString(),
                text = message.body,
                createdAt = message.createdAt.toString()
            )
        )

    fun messageNewFromEvent(
        messageId: UUID,
        chatId: UUID,
        senderId: UUID,
        text: String,
        createdAt: String
    ): Map<String, Any?> =
        mapOf(
            "type" to "message.new",
            "payload" to MessageNewPayload(
                id = messageId.toString(),
                chatId = chatId.toString(),
                senderId = senderId.toString(),
                text = text,
                createdAt = createdAt
            )
        )

    fun error(code: String, message: String, retryAfterSeconds: Long? = null): Map<String, Any?> =
        mapOf(
            "type" to "error",
            "payload" to WsErrorPayload(
                code = code,
                message = message,
                retryAfterSeconds = retryAfterSeconds
            )
        )
}
