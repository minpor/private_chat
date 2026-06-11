package chat.privatechat.application

import chat.privatechat.domain.User
import chat.privatechat.domain.ports.UserRepository
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class UserSearchService(
    private val userRepository: UserRepository
) {
    suspend fun searchUsers(requesterId: UUID, query: String, limit: Int): List<User> {
        val prefix = query.trim()
        if (prefix.length < 2) return emptyList()
        return userRepository.searchByUsernamePrefix(prefix, requesterId, limit.coerceIn(1, 50))
    }
}
