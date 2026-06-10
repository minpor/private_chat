package chat.privatechat.api.dto

import chat.privatechat.domain.Message
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class SendMessageRequest(
    @field:NotNull
    val clientMessageId: UUID,
    @field:NotBlank @field:Size(max = 8192)
    val text: String,
    val replyTo: UUID? = null
)

data class MessageAcceptedResponse(
    val id: String,
    val chatId: String,
    val clientMessageId: String,
    val status: String,
    val createdAt: String
)

data class MessageResponse(
    val id: String,
    val chatId: String,
    val senderId: String,
    val clientMessageId: String,
    val text: String,
    val replyTo: String?,
    val createdAt: String
)

data class MessageListResponse(
    val messages: List<MessageResponse>,
    val nextBefore: String?
)

fun Message.toAcceptedResponse(): MessageAcceptedResponse =
    MessageAcceptedResponse(
        id = id.toString(),
        chatId = chatId.toString(),
        clientMessageId = clientMessageId.toString(),
        status = status.name.lowercase(),
        createdAt = createdAt.toString()
    )

fun Message.toResponse(): MessageResponse =
    MessageResponse(
        id = id.toString(),
        chatId = chatId.toString(),
        senderId = senderId.toString(),
        clientMessageId = clientMessageId.toString(),
        text = body,
        replyTo = replyTo?.toString(),
        createdAt = createdAt.toString()
    )
