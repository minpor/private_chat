package chat.privatechat.infrastructure.redis

import chat.privatechat.domain.ports.ChatMemberLookup
import chat.privatechat.domain.ports.ChatRepository
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.data.redis.core.ReactiveStringRedisTemplate
import org.springframework.stereotype.Repository
import java.time.Duration
import java.util.UUID

@Repository
class RedisChatMemberLookup(
    private val redisTemplate: ReactiveStringRedisTemplate,
    private val chatRepository: ChatRepository,
    private val properties: ChatMemberCacheProperties
) : ChatMemberLookup {

    override suspend fun findMemberIds(chatId: UUID): List<UUID> {
        val cached = redisTemplate.opsForValue()
            .get(ChatMemberRedisKeys.membersKey(chatId))
            .awaitSingleOrNull()
        if (cached != null) {
            return decodeMemberIds(cached)
        }

        val memberIds = chatRepository.findMembers(chatId).map { it.userId }
        if (memberIds.isNotEmpty()) {
            remember(chatId, memberIds)
        }
        return memberIds
    }

    override suspend fun remember(chatId: UUID, memberIds: List<UUID>) {
        if (memberIds.isEmpty()) return
        redisTemplate.opsForValue()
            .set(
                ChatMemberRedisKeys.membersKey(chatId),
                encodeMemberIds(memberIds),
                Duration.ofMinutes(properties.ttlMinutes)
            )
            .awaitSingleOrNull()
    }

    private fun encodeMemberIds(memberIds: List<UUID>): String =
        memberIds.joinToString(MEMBER_ID_SEPARATOR) { it.toString() }

    private fun decodeMemberIds(value: String): List<UUID> =
        value.split(MEMBER_ID_SEPARATOR)
            .filter { it.isNotBlank() }
            .map { UUID.fromString(it) }

    private companion object {
        const val MEMBER_ID_SEPARATOR = ","
    }
}
