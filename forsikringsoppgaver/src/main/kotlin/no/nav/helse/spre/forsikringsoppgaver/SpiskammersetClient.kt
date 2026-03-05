package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.retry.retry
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking

class SpiskammersetClient(
    private val baseUrl: String,
    private val tokenClient: AzureTokenProvider,
    private val httpClient: HttpClient,
    private val spiskammersetScope: String,
) : ForsikringsgrunnlagClient {
    override fun forsikringsgrunnlag(behandlingId: BehandlingId): Forsikringsgrunnlag {
        return runBlocking {
            retry {
                val response = httpClient.prepareGet("$baseUrl/behandling/${behandlingId.value}/forsikring") {
                    accept(ContentType.Application.Json)
                    val bearerToken = tokenClient.bearerToken(spiskammersetScope).getOrThrow()
                    bearerAuth(bearerToken.token)
                }.execute()
                response.body<Forsikringsgrunnlag>()
            }
        }
    }
}

