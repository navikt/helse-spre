val postgresqlVersion = "42.5.4"
val kotliqueryVersion = "1.9.0"
val hikariCPVersion = "5.0.1"
val flywaycoreVersion = "9.15.0"

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywaycoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    testImplementation("org.testcontainers:postgresql:1.17.6")
}
