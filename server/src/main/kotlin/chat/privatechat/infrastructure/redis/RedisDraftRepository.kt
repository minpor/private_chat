package chat.privatechat.infrastructure.redis

import chat.privatechat.domain.DraftSnapshot
import chat.privatechat.domain.ports.DraftRepository
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisDraftRepository(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val objectMapper: ObjectMapper
) : DraftRepository {

    override suspend fun save(snapshot: DraftSnapshot, ttl: Duration) {
        val json = objectMapper.writeValueAsString(snapshot)
        val mainKey = DraftRedisKeys.userChatKey(snapshot.userId, snapshot.chatId)
        redisTemplate.opsForValue().set(mainKey, json, ttl).awaitSingleOrNull()

        val indexKey = DraftRedisKeys.byIdKey(snapshot.draftId)
        val indexValue = "${snapshot.userId}:${snapshot.chatId}"
        redisTemplate.opsForValue().set(indexKey, indexValue, ttl).awaitSingleOrNull()
    }

    override suspend fun findByUserAndChat(userId: UUID, chatId: UUID): DraftSnapshot? {
        val key = DraftRedisKeys.userChatKey(userId, chatId)
        val json = redisTemplate.opsForValue().get(key).awaitSingleOrNull() ?: return null
        return objectMapper.readValue(json, DraftSnapshot::class.java)
    }

    override suspend fun findByDraftId(draftId: UUID): DraftSnapshot? {
        val indexKey = DraftRedisKeys.byIdKey(draftId)
        val pointer = redisTemplate.opsForValue().get(indexKey).awaitSingleOrNull()
        val parts = pointer?.split(":", limit = 2)
        return if (parts?.size == 2) {
            findByUserAndChat(UUID.fromString(parts[0]), UUID.fromString(parts[1]))
        } else {
            null
        }
    }

    override suspend fun delete(userId: UUID, chatId: UUID, draftId: UUID) {
        val mainKey = DraftRedisKeys.userChatKey(userId, chatId)
        val indexKey = DraftRedisKeys.byIdKey(draftId)
        redisTemplate.delete(mainKey).awaitSingleOrNull()
        redisTemplate.delete(indexKey).awaitSingleOrNull()
    }
}
