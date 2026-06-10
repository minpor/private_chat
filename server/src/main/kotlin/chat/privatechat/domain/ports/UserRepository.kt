package chat.privatechat.domain.ports

import chat.privatechat.domain.User
import chat.privatechat.domain.UserCredentials
import java.util.UUID

/**
 * Порт персистентности пользователей (R2DBC, non-blocking).
 */
interface UserRepository {
    suspend fun findById(id: UUID): User?

    suspend fun findByUsername(username: String): User?

    suspend fun findCredentialsByUsername(username: String): UserCredentials?

    suspend fun existsByUsername(username: String): Boolean

    suspend fun insert(
        id: UUID,
        username: String,
        passwordHash: String,
        displayName: String
    ): User
}
