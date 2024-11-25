val tbdLibsVersion: String by project
val mockkVersion: String by project
val jsonSchemaValidatorVersion = "1.0.65"
val kotestAssertionsCoreVersion = "5.1.0"

dependencies {
    implementation("com.github.navikt.tbd-libs:azure-token-client-default:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:retry:$tbdLibsVersion")
    implementation("com.github.navikt.tbd-libs:spedisjon-client:$tbdLibsVersion")

    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestAssertionsCoreVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
