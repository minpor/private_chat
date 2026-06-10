package chat.privatechat.infrastructure.redis

import java.util.UUID

object ChatMemberRedisKeys {
    fun membersKey(chatId: UUID): String = "chat:members:$chatId"
}
