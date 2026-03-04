val tbdLibsVersion: String by project
val ktorVersion: String by project

dependencies {
    implementation("com.github.navikt.tbd-libs:azure-token-client-default:${tbdLibsVersion}")
    implementation("com.github.navikt.tbd-libs:retry:${tbdLibsVersion}")
    implementation("io.ktor:ktor-client-cio:${ktorVersion}")
    implementation("io.ktor:ktor-client-content-negotiation:${ktorVersion}")
    implementation("io.ktor:ktor-serialization-jackson:${ktorVersion}")
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation(kotlin("test"))
}
