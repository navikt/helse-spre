package no.nav.helse.spre.gosys.pdl

import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.spre.gosys.StsRestClient
import java.io.File
import java.util.*


class PdlClient(
    private val baseUrl: String,
    private val stsClient: StsRestClient,
    private val httpClient: io.ktor.client.HttpClient
) {
    val personinfoQuery: String


    init {
        File("/pdl/hentPersoninfo.graphql").apply {
            require(exists()) { "Finner ikke filen hentPersoninfo.graphql" }
            personinfoQuery = readText().replace(Regex("[\n\r]"), "")
        }
    }

    internal suspend fun hentPersonNavn(
        fødselsnummer: String,
        hendelseId: UUID,
    ): String? {
        val stsToken = stsClient.token()
        val payload = PdlQueryObject(
            query = personinfoQuery,
            variables = Variables(ident = fødselsnummer)
        )


        val response: HttpResponse = httpClient.post(Url(baseUrl)) {
            header("TEMA", "SYK")
            header("Authorization", "Bearer $stsToken")
            header("Nav-Consumer-Token", "Bearer $stsToken")
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
        return parsetRespons.data?.hentPerson?.firstOrNull()?.tilVisning()
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
