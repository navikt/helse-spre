val tbdLibsVersion: String by project
val mockkVersion: String by project
val jsonSchemaValidatorVersion = "1.0.65"
val kotestAssertionsCoreVersion = "5.1.0"

dependencies {
    testImplementation("com.github.navikt.tbd-libs:rapids-and-rivers-test:$tbdLibsVersion")
    testImplementation("com.networknt:json-schema-validator:$jsonSchemaValidatorVersion")
    testImplementation("io.kotest:kotest-assertions-core:$kotestAssertionsCoreVersion")
    testImplementation("io.mockk:mockk:$mockkVersion")
}
