val ktorVersion: String by project
val mockkVersion: String by project
val testcontainersVersion: String by project
val hikariCPVersion: String by project
val postgresqlVersion: String by project
val kotliqueryVersion: String by project
val flywayCoreVersion: String by project

val jsonassertVersion = "1.5.0"
val tbdLibsVersion = "2024.01.09-20.20-d52bae29"

val kotlinxSerializationJsonVersion = "1.6.0"

plugins {
    kotlin("plugin.serialization") version "1.6.0"
}

dependencies {
    implementation("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:$kotlinxSerializationJsonVersion")
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("io.mockk:mockk:$mockkVersion")
    testImplementation("org.skyscreamer:jsonassert:$jsonassertVersion")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}
