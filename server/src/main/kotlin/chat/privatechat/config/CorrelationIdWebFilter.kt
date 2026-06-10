package chat.privatechat.config

import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.server.ServerWebExchange
import org.springframework.web.server.WebFilter
import org.springframework.web.server.WebFilterChain
import reactor.core.publisher.Mono
import reactor.util.context.Context
import java.util.UUID

const val CORRELATION_ID_KEY = "correlationId"

/**
 * Пробрасывает X-Correlation-Id в ответ и MDC для structured logging.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class CorrelationIdWebFilter : WebFilter {

    override fun filter(exchange: ServerWebExchange, chain: WebFilterChain): Mono<Void> {
        val correlationId = exchange.request.headers.getFirst("X-Correlation-Id")?.takeIf { it.isNotBlank() }
            ?: UUID.randomUUID().toString()
        exchange.response.headers.add("X-Correlation-Id", correlationId)

        return chain.filter(exchange)
            .contextWrite { ctx: Context -> ctx.put(CORRELATION_ID_KEY, correlationId) }
            .doOnEach {
                if (it.isOnComplete || it.isOnError) {
                    MDC.remove(CORRELATION_ID_KEY)
                }
            }
            .doOnSubscribe { MDC.put(CORRELATION_ID_KEY, correlationId) }
    }
}
