package no.nav.helse.spre.gosys.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.spre.gosys.AzureClient
import java.util.*


class PdlClient(
    private val azureClient: AzureClient,
    private val httpClient: io.ktor.client.HttpClient,
    private val scope: String,
) {
    companion object {
        private val personinfoQuery = PdlClient::class.java.getResource("/pdl/hentPersoninfo.graphql").readText().replace(Regex("[\n\r]"), "")
    }

    internal suspend fun hentPersonNavn(
        fødselsnummer: String,
        hendelseId: UUID,
    ): String? {
        val token = azureClient.getToken(scope)
        val payload = PdlQueryObject(
            query = personinfoQuery,
            variables = Variables(ident = fødselsnummer)
        )


        val response: HttpResponse = httpClient.post(Url("http://pdl-api.pdl.svc.nais.local/graphql")) {
            header("TEMA", "SYK")
            header("Authorization", "Bearer ${token.accessToken}")
            header("Content-Type", "application/json")
            header("Accept", "application/json")
            header("Nav-Call-Id", hendelseId.toString())
            body = payload
        }

        response.status.let {
            if (!it.isSuccess()) throw RuntimeException("error (responseCode=$it) from PDL")
        }

        val parsetRespons: PdlResponse<PdlHentPerson> = response.receive()
        håndterErrors(parsetRespons)
        return parsetRespons.data?.hentPerson?.navn?.firstOrNull()?.tilVisning()
    }

    private fun håndterErrors(response: PdlResponse<PdlHentPerson>) {
        val errors = response.errors
        if (!errors.isNullOrEmpty()) {
            throw RuntimeException(errors.joinToString(" ") { it.message })
        }
    }
}

data class PdlQueryObject(
    val query: String,
    val variables: Variables
)

data class Variables(
    val ident: String
)
