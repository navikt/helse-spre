val rapidsAndRiversVersion: String by project
val ktorVersion: String by project

dependencies {
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    testImplementation("io.ktor:ktor-serialization-jackson:$ktorVersion")
}