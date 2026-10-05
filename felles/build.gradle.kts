plugins {
    id("no.nav.sykepenger.kotlin")
}

dependencies {
    api(libs.rapids.and.rivers)

    testImplementation(libs.ktor.server.content.negotiation)
    testImplementation(libs.ktor.serialization.jackson3)
}
