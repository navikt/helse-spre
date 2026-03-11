package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.azure.createAzureTokenClientFromEnvironment
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.jackson.*
import no.nav.helse.rapids_rivers.RapidApplication

fun main() {
    val env = System.getenv()
    val rapidApp = RapidApplication.create(env)
    val azureClient = createAzureTokenClientFromEnvironment(env)

    val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            jackson()
        }
    }

    val gosysScope = env.getValue("GOSYS_SCOPE")
    val gosysBaseUrl = env.getValue("GOSYS_BASE_URL")

    val gosysOppgaveClient = GosysOppgaveClient(
        baseUrl = gosysBaseUrl,
        tokenClient = azureClient,
        httpClient = httpClient,
        gosysScope = gosysScope
    )

    val spiskammersetBaseUrl = env.getValue("SPISKAMMERSET_BASE_URL")
    val spiskammersetScope = env.getValue("SPISKAMMERSET_SCOPE")

    val spiskammersetClient = SpiskammersetClient(
        baseUrl = spiskammersetBaseUrl,
        tokenClient = azureClient,
        httpClient = httpClient,
        spiskammersetScope = spiskammersetScope
    )

    SelvstendigUtbetaltEtterVentetidRiver(rapidApp, gosysOppgaveClient, spiskammersetClient)
    VedtakFattetRiver(rapidApp, gosysOppgaveClient, spiskammersetClient)
    SelvstendigIngenDagerIgjenRiver(rapidApp, gosysOppgaveClient, spiskammersetClient)
    rapidApp.start()
}
