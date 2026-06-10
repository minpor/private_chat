package chat.privatechat.api.dto

import chat.privatechat.application.AuthTokens
import chat.privatechat.domain.User
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class RegisterRequest(
    @field:NotBlank @field:Size(min = 3, max = 64)
    val username: String,
    @field:NotBlank @field:Size(min = 8, max = 128)
    val password: String,
    @field:NotBlank @field:Size(max = 128)
    val displayName: String
)

data class LoginRequest(
    @field:NotBlank
    val username: String,
    @field:NotBlank
    val password: String
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String
)

data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val user: UserResponse
)

data class UserResponse(
    val id: String,
    val username: String,
    val displayName: String
)

fun AuthTokens.toTokenResponse(): TokenResponse =
    TokenResponse(
        accessToken = accessToken,
        refreshToken = refreshToken,
        user = user.toResponse()
    )

fun User.toResponse(): UserResponse =
    UserResponse(
        id = id.toString(),
        username = username,
        displayName = displayName
    )
