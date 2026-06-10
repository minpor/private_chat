package chat.privatechat.api

import chat.privatechat.api.dto.MessageAcceptedResponse
import chat.privatechat.api.dto.MessageListResponse
import chat.privatechat.api.dto.MessageResponse
import chat.privatechat.api.dto.SendMessageRequest
import chat.privatechat.api.dto.toAcceptedResponse
import chat.privatechat.api.dto.toResponse
import chat.privatechat.application.MessageCommandService
import chat.privatechat.application.MessageQueryService
import chat.privatechat.config.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant
import java.util.UUID

/**
 * REST API сообщений: история и прямая отправка (fallback без draft-sync).
 */
@RestController
@RequestMapping("/api/v1/chats/{chatId}/messages")
class MessageController(
    private val messageCommandService: MessageCommandService,
    private val messageQueryService: MessageQueryService
) {
    @PostMapping
    suspend fun sendMessage(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @Valid @RequestBody request: SendMessageRequest
    ): ResponseEntity<MessageAcceptedResponse> {
        val message = messageCommandService.sendDirectMessage(
            chatId = chatId,
            senderId = principal.id,
            clientMessageId = request.clientMessageId,
            text = request.text,
            replyTo = request.replyTo
        )
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(message.toAcceptedResponse())
    }

    @GetMapping
    suspend fun listMessages(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @RequestParam(required = false) before: String?,
        @RequestParam(defaultValue = "50") limit: Int
    ): MessageListResponse {
        val beforeInstant = before?.let { Instant.parse(it) }
        val messages = messageQueryService.listMessages(chatId, principal.id, beforeInstant, limit)
        val nextBefore = messages.lastOrNull()?.createdAt?.toString()
        return MessageListResponse(
            messages = messages.map { it.toResponse() },
            nextBefore = if (messages.size >= limit.coerceIn(1, 100)) nextBefore else null
        )
    }

    @GetMapping("/{messageId}")
    suspend fun getMessage(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID,
        @PathVariable messageId: UUID
    ): MessageResponse =
        messageQueryService.getMessage(chatId, messageId, principal.id).toResponse()
}
