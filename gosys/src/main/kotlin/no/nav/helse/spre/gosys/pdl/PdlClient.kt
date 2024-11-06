package no.nav.helse.spre.gosys.pdl

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.spre.gosys.executeRetry
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.*


class PdlClient(
    private val azureClient: AzureTokenProvider,
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
        val payload = PdlQueryObject(
            query = personinfoQuery,
            variables = Variables(ident = fødselsnummer)
        )

        sikkerLogg.info("Henter navn på person: $fødselsnummer, {}", StructuredArguments.kv("Nav-Call-Id", hendelseId.toString()))

        return httpClient.preparePost(Url("$baseUrl/graphql")) {
            header("TEMA", "SYK")
            bearerAuth(azureClient.bearerToken(scope).getOrThrow().token)
            header("Content-Type", "application/json")
            header("Accept", "application/json")
            header("behandlingsnummer", "B139")
            header("Nav-Call-Id", hendelseId.toString())
            setBody(payload)
        }.executeRetry { response ->
            response.status.let {
                if (!it.isSuccess()) {
                    sikkerLogg.error("Feil henting av navn fra PDL. ResponseCode=$it, Body=${response.bodyAsText()}")
                    throw RuntimeException("error (responseCode=$it) from PDL")
                }
            }
            val parsetRespons: PdlResponse<PdlHentPerson> = response.body()
            håndterErrors(parsetRespons)
            parsetRespons.data?.hentPerson?.navn?.firstOrNull()?.tilVisning()
        }
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
