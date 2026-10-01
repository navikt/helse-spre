plugins {
    id("no.nav.sykepenger.deployable")
}

sykepengerDeployable {
    mainClass = "no.nav.helse.spre.sykmeldt.AppKt"
    imageName = "helse-spre-sykmeldt"
}

dependencies {
    implementation(project(":felles"))

    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
}
