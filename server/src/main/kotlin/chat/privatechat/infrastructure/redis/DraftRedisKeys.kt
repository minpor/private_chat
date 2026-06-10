package chat.privatechat.infrastructure.redis

import java.util.UUID

object DraftRedisKeys {
    fun userChatKey(userId: UUID, chatId: UUID): String = "draft:$userId:$chatId"

    fun byIdKey(draftId: UUID): String = "draft:by-id:$draftId"
}
