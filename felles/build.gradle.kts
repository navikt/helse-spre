val rapidsAndRiversVersion: String by project
val ktorVersion: String by project

dependencies {
    testImplementation("io.ktor:ktor-jackson:$ktorVersion")
    api("com.github.navikt:rapids-and-rivers:$rapidsAndRiversVersion")
}