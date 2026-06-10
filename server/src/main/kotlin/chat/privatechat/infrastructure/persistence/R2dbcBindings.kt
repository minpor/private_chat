package chat.privatechat.infrastructure.persistence

import org.springframework.r2dbc.core.DatabaseClient
import java.util.UUID

internal fun DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: String?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, String::class.java)

internal fun DatabaseClient.GenericExecuteSpec.bindNullable(
    name: String,
    value: UUID?
): DatabaseClient.GenericExecuteSpec =
    if (value != null) bind(name, value) else bindNull(name, UUID::class.java)
