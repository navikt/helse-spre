val flywayCoreVersion = "8.5.12"
val hikariCPVersion = "5.0.1"
val kotliqueryVersion = "1.8.0"
val postgresqlVersion = "42.5.0"
val jsonSchemaValidatorVersion = "1.0.65"
val kotestAssertionsCoreVersion = "5.1.0"
val testcontainersPostgresqlVersion = "1.16.3"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersPostgresqlVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestAssertionsCoreVersion")
}