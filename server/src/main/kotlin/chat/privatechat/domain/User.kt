package chat.privatechat.domain

import java.time.Instant
import java.util.UUID

/**
 * Зарегистрированный пользователь мессенджера.
 *
 * @property id UUID v7, сортируемый идентификатор.
 * @property username уникальный логин (латиница, цифры, подчёркивание).
 * @property displayName отображаемое имя в чатах.
 */
data class User(
    val id: UUID,
    val username: String,
    val displayName: String,
    val createdAt: Instant
)

/**
 * Учётные данные пользователя при аутентификации (включая хэш пароля).
 */
data class UserCredentials(
    val user: User,
    val passwordHash: String
)
