package chat.privatechat.api.dto

import chat.privatechat.domain.DraftSnapshot
import chat.privatechat.domain.Message
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.util.UUID

data class CreateDraftRequest(
    @field:NotNull
    val draftId: UUID,
    val clientSessionId: String? = null
)

data class UpsertDraftRequest(
    @field:NotNull
    val revision: Long,
    @field:NotBlank @field:Size(max = 8192)
    val text: String,
    val clientSessionId: String? = null
)

data class CommitDraftRequest(
    @field:NotNull
    val clientMessageId: UUID,
    val expectedRevision: Long? = null
)

data class DraftResponse(
    val draftId: String,
    val chatId: String,
    val revision: Long,
    val text: String,
    val clientSessionId: String?,
    val updatedAt: String
)

fun DraftSnapshot.toResponse(): DraftResponse =
    DraftResponse(
        draftId = draftId.toString(),
        chatId = chatId.toString(),
        revision = revision,
        text = text,
        clientSessionId = clientSessionId,
        updatedAt = updatedAt.toString()
    )
