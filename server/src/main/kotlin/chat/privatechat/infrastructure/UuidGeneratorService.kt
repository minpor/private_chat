package chat.privatechat.infrastructure

import chat.privatechat.domain.IdGenerator
import com.fasterxml.uuid.Generators
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class UuidGeneratorService : IdGenerator {
    private val generator = Generators.timeBasedEpochRandomGenerator()

    override fun nextId(): UUID = generator.generate()
}
