plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.spre.sykmeldt.AppKt"
    imageName = "helse-spre-sykmeldt"
}

dependencies {
    implementation(project(":felles"))

    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
}
