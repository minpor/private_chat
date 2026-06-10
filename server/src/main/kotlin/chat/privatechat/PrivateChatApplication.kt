package chat.privatechat

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class PrivateChatApplication

fun main(args: Array<String>) {
    runApplication<PrivateChatApplication>(*args)
}
