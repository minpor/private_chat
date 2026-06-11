package chat.privatechat.api.dto

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateDirectChatRequest(
    @field:NotBlank @field:Size(min = 3, max = 64)
    val username: String
)

data class ChatPeerResponse(
    val id: String,
    val username: String,
    val displayName: String
)

data class ChatResponse(
    val id: String,
    val type: String,
    val title: String?,
    val createdBy: String,
    val createdAt: String,
    val peer: ChatPeerResponse? = null
)

fun chat.privatechat.domain.User.toPeerResponse(): ChatPeerResponse =
    ChatPeerResponse(
        id = id.toString(),
        username = username,
        displayName = displayName
    )

fun Chat.toResponse(peer: chat.privatechat.domain.User? = null): ChatResponse =
    ChatResponse(
        id = id.toString(),
        type = type.name.lowercase(),
        title = title,
        createdBy = createdBy.toString(),
        createdAt = createdAt.toString(),
        peer = peer?.toPeerResponse()
    )
