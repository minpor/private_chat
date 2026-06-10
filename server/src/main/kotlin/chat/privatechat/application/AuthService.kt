package chat.privatechat.application

import chat.privatechat.domain.IdGenerator
import chat.privatechat.domain.User
import chat.privatechat.domain.ports.RefreshTokenRepository
import chat.privatechat.domain.ports.UserRepository
import chat.privatechat.infrastructure.jwt.JwtService
import chat.privatechat.infrastructure.jwt.TokenHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID

/**
 * Регистрация, вход и rotation refresh-токенов.
 *
 * Пароли хэшируются BCrypt на [Dispatchers.Default] (короткая CPU-bound операция).
 * Refresh хранится в PG как SHA-256 хэш.
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val tokenHasher: TokenHasher,
    private val idGenerator: IdGenerator
) {
    suspend fun register(username: String, password: String, displayName: String): AuthTokens {
        require(username.matches(USERNAME_PATTERN)) {
            "Username must be 3-64 chars: letters, digits, underscore"
        }
        require(password.length >= 8) { "Password must be at least 8 characters" }
        require(displayName.isNotBlank()) { "Display name is required" }
        if (userRepository.existsByUsername(username)) {
            throw UsernameTakenException(username)
        }

        val passwordHash = withContext(Dispatchers.Default) {
            passwordEncoder.encode(password)!!
        }
        val user = userRepository.insert(
            id = idGenerator.nextId(),
            username = username,
            passwordHash = passwordHash,
            displayName = displayName.trim()
        )
        return issueTokens(user)
    }

    suspend fun login(username: String, password: String): AuthTokens {
        val credentials = userRepository.findCredentialsByUsername(username)
            ?: throw InvalidCredentialsException()
        val matches = withContext(Dispatchers.Default) {
            passwordEncoder.matches(password, credentials.passwordHash)
        }
        if (!matches) {
            throw InvalidCredentialsException()
        }
        return issueTokens(credentials.user)
    }

    suspend fun refresh(refreshToken: String): AuthTokens {
        val hash = tokenHasher.hash(refreshToken)
        val record = refreshTokenRepository.findActiveByTokenHash(hash)
            ?: throw InvalidRefreshTokenException()
        val user = userRepository.findById(record.userId)
            ?: throw InvalidRefreshTokenException()

        refreshTokenRepository.revoke(record.id)
        return issueTokens(user)
    }

    private suspend fun issueTokens(user: User): AuthTokens {
        val accessToken = jwtService.createAccessToken(user.id, user.username)
        val refreshToken = generateOpaqueToken()
        refreshTokenRepository.insert(
            id = idGenerator.nextId(),
            userId = user.id,
            tokenHash = tokenHasher.hash(refreshToken),
            expiresAt = jwtService.refreshExpiresAt()
        )
        return AuthTokens(
            accessToken = accessToken,
            refreshToken = refreshToken,
            user = user
        )
    }

    private fun generateOpaqueToken(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    companion object {
        private val USERNAME_PATTERN = Regex("^[a-zA-Z0-9_]{3,64}$")
    }
}

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val user: User
)

class InvalidCredentialsException : RuntimeException("Invalid username or password")

class InvalidRefreshTokenException : RuntimeException("Invalid or expired refresh token")

class UsernameTakenException(username: String) : RuntimeException("Username already taken: $username")
