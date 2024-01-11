val testcontainersVersion: String by project
val hikariCPVersion: String by project
val postgresqlVersion: String by project
val kotliqueryVersion: String by project
val flywayCoreVersion: String by project

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
}
