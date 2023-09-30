val postgresqlVersion = "42.6.0"
val kotliqueryVersion = "1.9.0"
val hikariCPVersion = "5.0.1"
val flywaycoreVersion = "9.22.2"
val testcontainersVersion = "1.19.0"

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywaycoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}
