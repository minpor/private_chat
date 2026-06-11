package chat.privatechat

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import io.nats.client.JetStream
import org.junit.jupiter.api.Test
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

/**
 * Запрет blocking API в production-коде (см. docs/PLAN.md).
 */
class BlockingApiArchTest {

    private val productionCode = ClassFileImporter()
        .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
        .importPackages("chat.privatechat")

    @Test
    fun `must not use JDBC from main sources`() {
        noClasses()
            .should().accessClassesThat().resideInAnyPackage("java.sql..")
            .check(productionCode)
    }

    @Test
    fun `must not call Mono block`() {
        noClasses()
            .should().callMethod(Mono::class.java, "block")
            .check(productionCode)
    }

    @Test
    fun `must not call Flux blockFirst or blockLast`() {
        noClasses()
            .should().callMethod(Flux::class.java, "blockFirst")
            .check(productionCode)
        noClasses()
            .should().callMethod(Flux::class.java, "blockLast")
            .check(productionCode)
    }

    @Test
    fun `must not call JetStream publish synchronously`() {
        noClasses()
            .should().callMethod(JetStream::class.java, "publish")
            .check(productionCode)
    }

}
