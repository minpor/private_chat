package chat.privatechat.application

import kotlinx.serialization.Serializable

@Serializable
data class MessageCreatedNatsPayload(
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val text: String,
    val createdAt: String,
    val memberIds: List<String>
)
