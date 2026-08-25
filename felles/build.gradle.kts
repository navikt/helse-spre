plugins {
    id("no.nav.helse.sas.sas-kotlin")
}

dependencies {
    api(libs.rapids.and.rivers)

    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.jackson)
}
