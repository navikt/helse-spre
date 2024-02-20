val testcontainersVersion: String by project
val hikariCPVersion: String by project
val postgresqlVersion: String by project
val kotliqueryVersion: String by project
val flywayCoreVersion: String by project
val tbdLibsVersion: String by project

val skyscreamerVersion = "1.5.1"

dependencies {
    implementation("com.zaxxer:HikariCP:$hikariCPVersion")
    implementation("org.postgresql:postgresql:$postgresqlVersion")
    implementation("org.flywaydb:flyway-core:$flywayCoreVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayCoreVersion")
    implementation("com.github.seratch:kotliquery:$kotliqueryVersion")
    implementation("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")

    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.skyscreamer:jsonassert:$skyscreamerVersion")
}
