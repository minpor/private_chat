plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.spring) apply false
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.dokka) apply false
    alias(libs.plugins.flyway)
}

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath(libs.flyway.postgresql)
        classpath(libs.postgresql.jdbc)
    }
}

flyway {
    url = System.getenv("FLYWAY_URL")
        ?: "jdbc:postgresql://${System.getenv("DB_HOST") ?: "localhost"}:${System.getenv("DB_PORT") ?: "5432"}/${System.getenv("DB_NAME") ?: "private_chat"}"
    user = System.getenv("DB_USER") ?: "private_chat"
    password = System.getenv("DB_PASSWORD") ?: "your_password"
    locations = arrayOf("filesystem:server/src/main/resources/db/migration")
}
