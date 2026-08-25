plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.spre.styringsinfo.AppKt"
    imageName = "helse-spre-styringsinfo"
}

dependencies {
    implementation(project(":felles"))
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotliquery)
    implementation(libs.tbd.libs.azure)
    implementation(libs.tbd.libs.retry)
    implementation(libs.tbd.libs.speed.client)

    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.tbd.libs.postgres.testdatabaser)
    testImplementation(libs.jsonassert)
    testImplementation(libs.mockk)
}
