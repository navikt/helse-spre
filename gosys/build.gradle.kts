import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("no.nav.helse.sas.sas-deployable")
}

sasDeployable {
    mainClass = "no.nav.helse.spre.gosys.AppKt"
    imageName = "helse-spre-gosys"
}

dependencies {
    implementation(project(":felles"))
    implementation(libs.tbd.libs.azure)
    implementation(libs.tbd.libs.retry)
    implementation(libs.tbd.libs.speed.client)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.jackson)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.jackson)
    implementation(libs.ktor.server.auth.jwt) {
        exclude(group = "junit")
    }
    implementation(libs.hikaricp)
    implementation(libs.postgresql)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.kotliquery)

    testImplementation(libs.tbd.libs.postgres.testdatabaser)
    testImplementation(libs.tbd.libs.rapids.and.rivers.test)
    testImplementation(libs.mockk)
    testImplementation(libs.jsonassert)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.wiremock)
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.set(listOf("-Xcontext-parameters"))
    }
}

// ktlint-versjonen sas-module drar inn bruker en Kotlin-frontend som ikke forstår
// context parameters, og klarer derfor ikke å parse kildekoden i denne modulen.
ktlint {
    version = "1.8.0"
}
