val ktorVersion: String by project

val testcontainersPostgresqlVersion = "1.17.2"
val jsonassertVersion = "1.5.0"
val kotliqueryVersion = "1.6.0"
val flywayCoreVersion = "8.4.1"
val vaultJdbcVersion = "1.3.10"
val hikariCPVersion = "5.0.1"
val kotlinxSerializationJsonVersion = "1.3.2"

plugins {
    kotlin("plugin.serialization") version "1.4.30"
}

dependencies {
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}
