val ktorVersion: String by project

plugins {
    kotlin("plugin.serialization") version "1.4.30"
}

dependencies {
    implementation("io.ktor:ktor-client-cio:$ktorVersion")
    implementation("io.ktor:ktor-client-jackson:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.0.1")
    implementation("com.zaxxer:HikariCP:4.0.3")
    implementation("no.nav:vault-jdbc:1.3.7")
    implementation("org.flywaydb:flyway-core:7.7.1")
    implementation("com.github.seratch:kotliquery:1.3.1")

    testImplementation("org.skyscreamer:jsonassert:1.5.0")

    testImplementation("io.ktor:ktor-client-mock-jvm:$ktorVersion")
    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("com.opentable.components:otj-pg-embedded:0.13.3")
}
