val ktorVersion: String by project

val flywayCoreVersion = "8.5.12"
val vaultJdbcVersion = "1.3.9"
val hikariCPVersion = "5.0.1"
val kotliqueryVersion = "1.8.0"
val kafkaEmbeddedEnvVersion = "3.1.6"
val awaitilityVersion = "4.2.0"
val postgresqlVersion = "1.17.2"

dependencies {
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("no.nav:kafka-embedded-env:$kafkaEmbeddedEnvVersion")
    testImplementation("org.awaitility:awaitility:$awaitilityVersion")
    testImplementation("org.testcontainers:postgresql:$postgresqlVersion")
}
