val ktorVersion: String by project

val kotliqueryVersion = "1.7.0"
val flywayCoreVersion = "8.5.5"
val vaultJdbcVersion = "1.3.10"
val hikariCPVersion = "5.0.1"
val testcontainersPostgresqlVersion = "1.16.3"

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

    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
}
