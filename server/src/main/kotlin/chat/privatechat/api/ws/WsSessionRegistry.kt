package chat.privatechat.api.ws

import tools.jackson.databind.json.JsonMapper
import kotlinx.coroutines.reactor.awaitSingleOrNull
import org.springframework.stereotype.Component
import org.springframework.web.reactive.socket.WebSocketSession
import reactor.core.publisher.Mono
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class WsSessionRegistry(
    private val jsonMapper: JsonMapper
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
        val json = jsonMapper.writeValueAsString(frame)
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
