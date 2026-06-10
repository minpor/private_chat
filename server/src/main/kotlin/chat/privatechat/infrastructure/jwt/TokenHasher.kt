package chat.privatechat.infrastructure.jwt

import org.springframework.stereotype.Component
import java.security.MessageDigest

@Component
class TokenHasher {
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
