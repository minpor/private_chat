package chat.privatechat.application

import chat.privatechat.domain.DraftSnapshot
import chat.privatechat.domain.ports.DraftRepository
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.UUID

/**
 * Управление эфемерными черновиками в Redis.
 *
 * Redis key: `draft:{userId}:{chatId}` — см. [chat.privatechat.infrastructure.redis.DraftRedisKeys].
 */
@Service
class DraftService(
    private val draftRepository: DraftRepository,
    private val chatService: ChatService,
    private val rateLimitService: RateLimitService,
    private val draftProperties: chat.privatechat.infrastructure.redis.DraftProperties
) {
    private val draftTtl: Duration
        get() = Duration.ofMinutes(draftProperties.ttlMinutes)

    suspend fun startDraft(
        userId: UUID,
        chatId: UUID,
        draftId: UUID,
        clientSessionId: String?
    ): DraftSnapshot {
        chatService.requireMembership(chatId, userId)

        val snapshot = DraftSnapshot(
            draftId = draftId,
            chatId = chatId,
            userId = userId,
            text = "",
            revision = 0,
            clientSessionId = clientSessionId,
            updatedAt = Instant.now()
        )
        draftRepository.save(snapshot, draftTtl)
        return snapshot
    }

    suspend fun applyPatch(
        userId: UUID,
        chatId: UUID,
        draftId: UUID,
        revision: Long,
        text: String
    ): DraftSnapshot {
        chatService.requireMembership(chatId, userId)
        rateLimitService.checkPatchLimit(userId, chatId)

        val existing = draftRepository.findByUserAndChat(userId, chatId)
            ?: throw DraftNotFoundException(chatId = chatId, draftId = draftId)

        if (existing.draftId != draftId) {
            throw DraftNotFoundException(chatId = chatId, draftId = draftId)
        }

        if (revision <= existing.revision) {
            return existing
        }

        val updated = existing.copy(
            text = text,
            revision = revision,
            updatedAt = Instant.now()
        )
        draftRepository.save(updated, draftTtl)
        return updated
    }

    @Suppress("LongParameterList")
    suspend fun upsertDraft(
        userId: UUID,
        chatId: UUID,
        draftId: UUID,
        revision: Long,
        text: String,
        clientSessionId: String?
    ): DraftSnapshot {
        val existing = draftRepository.findByUserAndChat(userId, chatId)
        return if (existing == null) {
            chatService.requireMembership(chatId, userId)
            val snapshot = DraftSnapshot(
                draftId = draftId,
                chatId = chatId,
                userId = userId,
                text = text,
                revision = revision,
                clientSessionId = clientSessionId,
                updatedAt = Instant.now()
            )
            draftRepository.save(snapshot, draftTtl)
            snapshot
        } else {
            applyPatch(userId, chatId, draftId, revision, text)
        }
    }

    suspend fun getSnapshot(userId: UUID, chatId: UUID): DraftSnapshot? =
        draftRepository.findByUserAndChat(userId, chatId)

    suspend fun getSnapshotByDraftId(userId: UUID, chatId: UUID, draftId: UUID): DraftSnapshot {
        val snapshot = draftRepository.findByUserAndChat(userId, chatId)
            ?: throw DraftNotFoundException(chatId = chatId, draftId = draftId)
        if (snapshot.draftId != draftId) {
            throw DraftNotFoundException(chatId = chatId, draftId = draftId)
        }
        return snapshot
    }

    suspend fun requireSnapshot(userId: UUID, chatId: UUID): DraftSnapshot =
        getSnapshot(userId, chatId) ?: throw DraftNotFoundException(chatId = chatId)

    suspend fun validateRevision(snapshot: DraftSnapshot, expectedRevision: Long?) {
        if (expectedRevision != null && expectedRevision != snapshot.revision) {
            throw DraftRevisionConflictException(snapshot)
        }
    }

    suspend fun discardDraft(userId: UUID, chatId: UUID, draftId: UUID) {
        val snapshot = draftRepository.findByUserAndChat(userId, chatId) ?: return
        if (snapshot.draftId == draftId) {
            draftRepository.delete(userId, chatId, draftId)
        }
    }

    suspend fun deleteAfterCommit(snapshot: DraftSnapshot) {
        draftRepository.delete(snapshot.userId, snapshot.chatId, snapshot.draftId)
    }
}
