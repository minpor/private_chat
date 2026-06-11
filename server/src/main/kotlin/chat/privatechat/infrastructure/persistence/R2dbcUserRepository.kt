package chat.privatechat.infrastructure.persistence

import chat.privatechat.domain.User
import chat.privatechat.domain.UserCredentials
import chat.privatechat.domain.ports.UserRepository
import kotlinx.coroutines.reactor.awaitSingle
import org.springframework.r2dbc.core.DatabaseClient
import org.springframework.r2dbc.core.awaitOneOrNull
import org.springframework.stereotype.Repository
import java.util.UUID

/**
 * R2DBC-адаптер пользователей. Non-blocking, suspend only.
 */
@Repository
class R2dbcUserRepository(
    private val databaseClient: DatabaseClient
) : UserRepository {

    override suspend fun findById(id: UUID): User? =
        databaseClient.sql(
            """
            SELECT id, username, display_name, created_at
            FROM users
            WHERE id = :id
            """.trimIndent()
        )
            .bind("id", id)
            .map { row, _ -> row.toUser() }
            .awaitOneOrNull()

    override suspend fun findByUsername(username: String): User? =
        databaseClient.sql(
            """
            SELECT id, username, display_name, created_at
            FROM users
            WHERE username = :username
            """.trimIndent()
        )
            .bind("username", username)
            .map { row, _ -> row.toUser() }
            .awaitOneOrNull()

    override suspend fun findCredentialsByUsername(username: String): UserCredentials? =
        databaseClient.sql(
            """
            SELECT id, username, password_hash, display_name, created_at
            FROM users
            WHERE username = :username
            """.trimIndent()
        )
            .bind("username", username)
            .map { row, _ -> row.toUserCredentials() }
            .awaitOneOrNull()

    override suspend fun existsByUsername(username: String): Boolean =
        databaseClient.sql("SELECT 1 AS one FROM users WHERE username = :username LIMIT 1")
            .bind("username", username)
            .map { _, _ -> true }
            .awaitOneOrNull() ?: false

    override suspend fun searchByUsernamePrefix(prefix: String, excludeUserId: UUID, limit: Int): List<User> =
        databaseClient.sql(
            """
            SELECT id, username, display_name, created_at
            FROM users
            WHERE username ILIKE :prefix
              AND id != :exclude_id
            ORDER BY username
            LIMIT :limit
            """.trimIndent()
        )
            .bind("prefix", "$prefix%")
            .bind("exclude_id", excludeUserId)
            .bind("limit", limit)
            .map { row, _ -> row.toUser() }
            .all()
            .collectList()
            .awaitSingle()

    override suspend fun insert(
        id: UUID,
        username: String,
        passwordHash: String,
        displayName: String
    ): User =
        databaseClient.sql(
            """
            INSERT INTO users (id, username, password_hash, display_name)
            VALUES (:id, :username, :password_hash, :display_name)
            RETURNING id, username, display_name, created_at
            """.trimIndent()
        )
            .bind("id", id)
            .bind("username", username)
            .bind("password_hash", passwordHash)
            .bind("display_name", displayName)
            .map { row, _ -> row.toUser() }
            .one()
            .awaitSingle()
}
