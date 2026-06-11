package chat.privatechat.api

import chat.privatechat.api.dto.UserResponse
import chat.privatechat.api.dto.toResponse
import chat.privatechat.application.UserSearchService
import chat.privatechat.config.UserPrincipal
import chat.privatechat.domain.ports.UserRepository
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users")
class UserController(
    private val userRepository: UserRepository,
    private val userSearchService: UserSearchService
) {
    @GetMapping("/me")
    suspend fun me(@AuthenticationPrincipal principal: UserPrincipal): UserResponse {
        val user = userRepository.findById(principal.id)
            ?: throw IllegalStateException("Authenticated user not found")
        return user.toResponse()
    }

    @GetMapping("/search")
    suspend fun search(
        @AuthenticationPrincipal principal: UserPrincipal,
        @RequestParam q: String,
        @RequestParam(defaultValue = "20") limit: Int
    ): List<UserResponse> =
        userSearchService.searchUsers(principal.id, q, limit).map { it.toResponse() }
}
