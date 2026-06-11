package chat.privatechat.api

import chat.privatechat.api.dto.ChatResponse
import chat.privatechat.api.dto.CreateDirectChatRequest
import chat.privatechat.api.dto.toResponse
import chat.privatechat.application.ChatService
import chat.privatechat.config.UserPrincipal
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/chats")
class ChatController(
    private val chatService: ChatService
) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun createDirectChat(
        @AuthenticationPrincipal principal: UserPrincipal,
        @Valid @RequestBody request: CreateDirectChatRequest
    ): ChatResponse {
        val chat = chatService.createDirectChat(principal.id, request.username)
        val peer = chatService.resolveDirectChatPeer(chat, principal.id)
        return chat.toResponse(peer)
    }

    @GetMapping
    suspend fun listChats(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam(defaultValue = "50") limit: Int
    ): List<ChatResponse> =
        chatService.listChats(principal.id, limit).map { chat ->
            val peer = chatService.resolveDirectChatPeer(chat, principal.id)
            chat.toResponse(peer)
        }

    @GetMapping("/{chatId}")
    suspend fun getChat(
        @AuthenticationPrincipal principal: UserPrincipal,
        @PathVariable chatId: UUID
    ): ChatResponse {
        val chat = chatService.getChat(chatId, principal.id)
        val peer = chatService.resolveDirectChatPeer(chat, principal.id)
        return chat.toResponse(peer)
    }
}
