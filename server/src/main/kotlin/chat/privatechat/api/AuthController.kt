package chat.privatechat.api

import chat.privatechat.api.dto.LoginRequest
import chat.privatechat.api.dto.RefreshRequest
import chat.privatechat.api.dto.RegisterRequest
import chat.privatechat.api.dto.TokenResponse
import chat.privatechat.api.dto.toTokenResponse
import chat.privatechat.application.AuthService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * Аутентификация: регистрация, вход, обновление access token.
 */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService
) {
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    suspend fun register(@Valid @RequestBody request: RegisterRequest): TokenResponse =
        authService.register(request.username, request.password, request.displayName).toTokenResponse()

    @PostMapping("/login")
    suspend fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        authService.login(request.username, request.password).toTokenResponse()

    @PostMapping("/refresh")
    suspend fun refresh(@Valid @RequestBody request: RefreshRequest): TokenResponse =
        authService.refresh(request.refreshToken).toTokenResponse()
}
