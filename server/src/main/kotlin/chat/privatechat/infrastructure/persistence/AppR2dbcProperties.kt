package chat.privatechat.infrastructure.persistence

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.r2dbc")
data class AppR2dbcProperties(
    val readReplica: ReadReplicaProperties = ReadReplicaProperties()
)

data class ReadReplicaProperties(
    val enabled: Boolean = false,
    val host: String = "",
    val port: Int = 5432,
    val database: String = "",
    val username: String = "",
    val password: String = ""
)
