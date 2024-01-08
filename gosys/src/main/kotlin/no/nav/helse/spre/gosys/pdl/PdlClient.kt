package no.nav.helse.spre.gosys.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.spre.gosys.AzureClient
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.*


class PdlClient(
    private val azureClient: AzureClient,
    private val httpClient: io.ktor.client.HttpClient,
    private val baseUrl: String,
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

        sikkerLogg.info("Henter navn på person: $fødselsnummer, {}", StructuredArguments.kv("Nav-Call-Id", hendelseId.toString()))
        val response: HttpResponse = httpClient.post(Url("$baseUrl/graphql")) {
            header("TEMA", "SYK")
            header("Authorization", "Bearer ${token.accessToken}")
            header("Content-Type", "application/json")
            header("Accept", "application/json")
            header("behandlingsnummer", "B139")
            header("Nav-Call-Id", hendelseId.toString())
            setBody(payload)
        }

        response.status.let {
            if (!it.isSuccess()) {
                sikkerLogg.error("Feil henting av navn fra PDL. ResponseCode=$it, Body=${response.bodyAsText()}")
                throw RuntimeException("error (responseCode=$it) from PDL")
            }
        }

        val parsetRespons: PdlResponse<PdlHentPerson> = response.body()
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
