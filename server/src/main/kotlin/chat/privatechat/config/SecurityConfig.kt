package chat.privatechat.config

import chat.privatechat.infrastructure.jwt.JwtService
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.ReactiveAuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity
import org.springframework.security.config.web.server.SecurityWebFiltersOrder
import org.springframework.security.config.web.server.ServerHttpSecurity
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.web.server.SecurityWebFilterChain
import org.springframework.security.web.server.authentication.AuthenticationWebFilter
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter
import org.springframework.security.web.server.authentication.ServerAuthenticationEntryPointFailureHandler
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono

@Configuration
@EnableWebFluxSecurity
class SecurityConfig(
    private val jwtService: JwtService
) {

    @Bean
    fun securityWebFilterChain(http: ServerHttpSecurity): SecurityWebFilterChain {
        val authManager = ReactiveAuthenticationManager { authentication ->
            Mono.fromCallable {
                val token = authentication.credentials as String
                val claims = jwtService.parseAccessToken(token)
                UsernamePasswordAuthenticationToken(
                    UserPrincipal(claims.userId, claims.username),
                    token,
                    listOf(SimpleGrantedAuthority("ROLE_USER"))
                )
            }
        }

        val jwtFilter = AuthenticationWebFilter(authManager).apply {
            setServerAuthenticationConverter(bearerTokenConverter())
            setRequiresAuthenticationMatcher(
                ServerWebExchangeMatchers.pathMatchers("/api/**")
            )
            setAuthenticationFailureHandler(
                ServerAuthenticationEntryPointFailureHandler { _, _ ->
                    Mono.error(org.springframework.security.access.AccessDeniedException("Unauthorized"))
                }
            )
        }

        return http
            .csrf { it.disable() }
            .httpBasic { it.disable() }
            .formLogin { it.disable() }
            .authorizeExchange { exchanges ->
                exchanges
                    .pathMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login", "/api/v1/auth/refresh")
                    .permitAll()
                    .pathMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus")
                    .permitAll()
                    .pathMatchers("/api/v1/ws")
                    .permitAll()
                    .pathMatchers("/api/**")
                    .authenticated()
                    .anyExchange()
                    .permitAll()
            }
            .exceptionHandling { handling ->
                handling.authenticationEntryPoint { _, _ ->
                    Mono.error(BadCredentialsException("Unauthorized"))
                }
                handling.accessDeniedHandler { _, _ ->
                    Mono.error(AccessDeniedException("Forbidden"))
                }
            }
            .addFilterAt(jwtFilter, SecurityWebFiltersOrder.AUTHENTICATION)
            .build()
    }

    private fun bearerTokenConverter(): ServerAuthenticationConverter =
        ServerAuthenticationConverter { exchange: ServerWebExchange ->
            val header = exchange.request.headers.getFirst("Authorization")
            if (header != null && header.startsWith("Bearer ", ignoreCase = true)) {
                val token = header.substring(7).trim()
                if (token.isNotEmpty()) {
                    return@ServerAuthenticationConverter Mono.just(
                        UsernamePasswordAuthenticationToken(null, token)
                    )
                }
            }
            Mono.empty()
        }
}

data class UserPrincipal(
    val id: java.util.UUID,
    val username: String
)
