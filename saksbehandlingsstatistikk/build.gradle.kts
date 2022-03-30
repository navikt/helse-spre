val ktorVersion: String by project

dependencies {
    implementation("io.ktor:ktor-jackson:$ktorVersion")
    implementation("io.ktor:ktor-auth-jwt:$ktorVersion") {
        exclude(group = "junit")
    }

    implementation("com.zaxxer:HikariCP:5.0.1")
    implementation("no.nav:vault-jdbc:1.3.9")
    implementation("org.flywaydb:flyway-core:8.5.5")
    implementation("com.github.seratch:kotliquery:1.7.0")

    testImplementation("org.testcontainers:postgresql:1.16.3")
}

