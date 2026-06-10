package chat.privatechat.config

import chat.privatechat.infrastructure.persistence.AppR2dbcProperties
import io.r2dbc.pool.ConnectionPool
import io.r2dbc.pool.ConnectionPoolConfiguration
import io.r2dbc.spi.ConnectionFactory
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.r2dbc.autoconfigure.R2dbcProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.r2dbc.ConnectionFactoryBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.r2dbc.core.DatabaseClient

/**
 * Primary R2DBC — writes и read-your-writes.
 * Опциональный read-replica [DatabaseClient] для истории сообщений (GET /messages).
 */
@Configuration
@EnableConfigurationProperties(AppR2dbcProperties::class)
class R2dbcConfig {

    @Bean
    @Qualifier("readDatabaseClient")
    fun readDatabaseClient(
        connectionFactory: ConnectionFactory,
        appR2dbcProperties: AppR2dbcProperties,
        springR2dbcProperties: R2dbcProperties
    ): DatabaseClient {
        val replica = appR2dbcProperties.readReplica
        if (!replica.enabled || replica.host.isBlank()) {
            return DatabaseClient.create(connectionFactory)
        }

        val primaryUrl = springR2dbcProperties.url ?: "r2dbc:postgresql://localhost:5432/private_chat"
        val database = replica.database.ifBlank { extractDatabaseName(primaryUrl) }
        val username = replica.username.ifBlank { springR2dbcProperties.username ?: "private_chat" }
        val password = replica.password.ifBlank { springR2dbcProperties.password ?: "" }

        val replicaFactory = ConnectionFactoryBuilder.withUrl(
            "r2dbc:postgresql://${replica.host}:${replica.port}/$database"
        )
            .username(username)
            .password(password)
            .build()

        val pool = springR2dbcProperties.pool
        val pooledReplica = if (pool.isEnabled) {
            ConnectionPool(
                ConnectionPoolConfiguration.builder(replicaFactory)
                    .initialSize(pool.initialSize)
                    .maxSize(pool.maxSize)
                    .maxIdleTime(pool.maxIdleTime)
                    .maxAcquireTime(pool.maxAcquireTime)
                    .maxCreateConnectionTime(pool.maxCreateConnectionTime)
                    .build()
            )
        } else {
            replicaFactory
        }

        return DatabaseClient.create(pooledReplica)
    }

    private fun extractDatabaseName(url: String): String {
        val path = url.substringAfterLast('/').substringBefore('?')
        return path.ifBlank { "private_chat" }
    }
}
