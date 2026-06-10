package chat.privatechat.api.ws

import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class WsSessionRegistry(
    private val objectMapper: ObjectMapper
) {
    private val sessions = ConcurrentHashMap<UUID, MutableSet<WebSocketSession>>()

    fun register(userId: UUID, session: WebSocketSession) {
        sessions.computeIfAbsent(userId) { ConcurrentHashMap.newKeySet() }.add(session)
    }

    fun unregister(userId: UUID, session: WebSocketSession) {
        sessions[userId]?.remove(session)
        if (sessions[userId]?.isEmpty() == true) {
            sessions.remove(userId)
        }
    }

    suspend fun sendToUser(userId: UUID, frame: Map<String, Any?>) {
        val json = objectMapper.writeValueAsString(frame)
        sessions[userId]?.forEach { session ->
            if (session.isOpen) {
                session.send(Mono.just(session.textMessage(json))).awaitSingleOrNull()
            }
        }
    }

    suspend fun broadcastToUsers(userIds: Collection<UUID>, frame: Map<String, Any?>) {
        userIds.forEach { sendToUser(it, frame) }
    }
}
