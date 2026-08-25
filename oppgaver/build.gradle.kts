plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.spre.oppgaver.AppKt"
    imageName = "helse-spre-oppgaver"
}

dependencies {
    implementation(project(":felles"))
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotliquery)

    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.tbd.libs.postgres.testdatabaser)
}
