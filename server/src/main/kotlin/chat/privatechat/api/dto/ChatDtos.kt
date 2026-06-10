package chat.privatechat.api.dto

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class CreateDirectChatRequest(
    @field:NotBlank @field:Size(min = 3, max = 64)
    val username: String
)

data class ChatResponse(
    val id: String,
    val type: String,
    val title: String?,
    val createdBy: String,
    val createdAt: String
)

fun Chat.toResponse(): ChatResponse =
    ChatResponse(
        id = id.toString(),
        type = type.name.lowercase(),
        title = title,
        createdBy = createdBy.toString(),
        createdAt = createdAt.toString()
    )
