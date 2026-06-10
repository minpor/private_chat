package chat.privatechat.domain

import java.util.UUID

/**
 * Генерация идентификаторов UUID v7 (time-ordered) для keyset-пагинации.
 */
fun interface IdGenerator {
    fun nextId(): UUID
}
