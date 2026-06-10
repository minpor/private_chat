package chat.privatechat.api

import chat.privatechat.api.dto.CommitDraftRequest
import chat.privatechat.api.dto.CreateDraftRequest
import chat.privatechat.api.dto.DraftResponse
import chat.privatechat.api.dto.UpsertDraftRequest
import chat.privatechat.api.dto.toAcceptedResponse
import chat.privatechat.api.dto.toResponse
import chat.privatechat.api.dto.MessageAcceptedResponse
import chat.privatechat.application.DraftService
import chat.privatechat.application.MessageCommandService
import chat.privatechat.config.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

/**
 * REST fallback для draft-sync (reconnect, клиенты без WebSocket).
 */
@RestController
@RequestMapping("/api/v1/chats/{chatId}/drafts")
class DraftController(
    private val draftService: DraftService,
    private val messageCommandService: MessageCommandService
) {
    @PostMapping
    suspend fun createDraft(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @Valid @RequestBody request: CreateDraftRequest
    ): DraftResponse {
        val snapshot = draftService.startDraft(
            userId = principal.id,
            chatId = chatId,
            draftId = request.draftId,
            clientSessionId = request.clientSessionId
        )
        return snapshot.toResponse()
    }

    @PutMapping("/{draftId}")
    suspend fun upsertDraft(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @PathVariable draftId: UUID,
        @Valid @RequestBody request: UpsertDraftRequest
    ): DraftResponse {
        val snapshot = draftService.upsertDraft(
            userId = principal.id,
            chatId = chatId,
            draftId = draftId,
            revision = request.revision,
            text = request.text,
            clientSessionId = request.clientSessionId
        )
        return snapshot.toResponse()
    }

    @GetMapping("/{draftId}")
    suspend fun getDraft(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @PathVariable draftId: UUID
    ): DraftResponse =
        draftService.getSnapshotByDraftId(principal.id, chatId, draftId).toResponse()

    @PostMapping("/{draftId}/commit")
    suspend fun commitDraft(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @PathVariable draftId: UUID,
        @Valid @RequestBody request: CommitDraftRequest
    ): ResponseEntity<MessageAcceptedResponse> {
        val snapshot = draftService.getSnapshotByDraftId(principal.id, chatId, draftId)
        val message = messageCommandService.commitFromDraft(
            snapshot = snapshot,
            clientMessageId = request.clientMessageId,
            expectedRevision = request.expectedRevision
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(message.toAcceptedResponse())
    }

    @DeleteMapping("/{draftId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    suspend fun discardDraft(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @PathVariable draftId: UUID
    ) {
        draftService.discardDraft(principal.id, chatId, draftId)
    }
}
