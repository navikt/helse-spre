plugins {
    id("no.nav.sykepenger.deployable")
}

sykepengerDeployable {
    mainClass = "no.nav.helse.spre.subsumsjon.AppKt"
    imageName = "helse-spre-subsumsjon"
}

dependencies {
    implementation(project(":felles"))

    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.kotest.assertions.core)
    testImplementation(libs.mockk)
}
