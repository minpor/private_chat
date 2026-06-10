package chat.privatechat.domain.ports

import chat.privatechat.domain.DraftSnapshot
import java.time.Duration
import java.util.UUID

/**
 * Эфемерное хранилище черновиков (Redis).
 */
interface DraftRepository {
    suspend fun save(snapshot: DraftSnapshot, ttl: Duration)

    suspend fun findByUserAndChat(userId: UUID, chatId: UUID): DraftSnapshot?

    suspend fun findByDraftId(draftId: UUID): DraftSnapshot?

    suspend fun delete(userId: UUID, chatId: UUID, draftId: UUID)
}
