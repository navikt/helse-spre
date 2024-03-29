val testcontainersVersion: String by project
val hikariCPVersion: String by project
val postgresqlVersion: String by project
val kotliqueryVersion: String by project
val flywayCoreVersion: String by project

val jsonSchemaValidatorVersion = "1.0.65"
val kotestAssertionsCoreVersion = "5.1.0"

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestAssertionsCoreVersion")
}
