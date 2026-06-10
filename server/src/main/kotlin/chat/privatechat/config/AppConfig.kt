package chat.privatechat.config

import chat.privatechat.infrastructure.jwt.JwtProperties
import chat.privatechat.infrastructure.nats.NatsProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableConfigurationProperties(JwtProperties::class, NatsProperties::class)
@EnableTransactionManagement
class AppConfig {

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
