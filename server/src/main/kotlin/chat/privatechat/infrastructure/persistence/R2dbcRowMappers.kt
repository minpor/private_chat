package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.Chat
import chat.privatechat.domain.ChatMember
import chat.privatechat.domain.ChatType
import chat.privatechat.domain.Message
import chat.privatechat.domain.User
import chat.privatechat.domain.UserCredentials
import io.r2dbc.spi.Row
import java.time.Instant
import java.util.UUID

internal fun Row.toUser(): User = User(
    id = get("id", UUID::class.java)!!,
    username = get("username", String::class.java)!!,
    displayName = get("display_name", String::class.java)!!,
    createdAt = get("created_at", Instant::class.java)!!
)

internal fun Row.toUserCredentials(): UserCredentials = UserCredentials(
    user = toUser(),
    passwordHash = get("password_hash", String::class.java)!!
)

internal fun Row.toChat(): Chat = Chat(
    id = get("id", UUID::class.java)!!,
    type = ChatType.valueOf(get("type", String::class.java)!!.uppercase()),
    title = get("title", String::class.java),
    createdBy = get("created_by", UUID::class.java)!!,
    createdAt = get("created_at", Instant::class.java)!!
)

internal fun Row.toChatMember(): ChatMember = ChatMember(
    chatId = get("chat_id", UUID::class.java)!!,
    userId = get("user_id", UUID::class.java)!!,
    joinedAt = get("joined_at", Instant::class.java)!!
)

internal fun Row.toMessage(): Message = Message(
    id = get("id", UUID::class.java)!!,
    chatId = get("chat_id", UUID::class.java)!!,
    senderId = get("sender_id", UUID::class.java)!!,
    clientMessageId = get("client_msg_id", UUID::class.java)!!,
    body = get("body", String::class.java)!!,
    replyTo = get("reply_to", UUID::class.java),
    createdAt = get("created_at", Instant::class.java)!!,
    deletedAt = get("deleted_at", Instant::class.java)
)
