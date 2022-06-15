val hikariCPVersion = "5.0.1"
val vaultJdbcVersion = "1.3.9"
val flywayCoreVersion = "8.5.12"
val kotliqueryVersion = "1.6.0"
val postgresqlVersion = "1.17.2"

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("no.nav:vault-jdbc:$vaultJdbcVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    testImplementation("org.testcontainers:postgresql:$postgresqlVersion")
}
