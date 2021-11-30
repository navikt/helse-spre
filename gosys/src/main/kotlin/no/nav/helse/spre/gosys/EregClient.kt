package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.util.*

class EregClient(
    private val baseUrl: String,
    private val stsRestClient: StsRestClient,
    private val httpClient: HttpClient,
) {
    suspend fun hentOrganisasjonsnavn(
        organisasjonsnummer: String,
        callId: UUID,
    ) = hentNavnFraEreg(organisasjonsnummer, callId)

    private suspend fun hentNavnFraEreg(
        organisasjonsnummer: String,
        callId: UUID,
    ): EregResponse {
        try {
            //TODO finn ut om vi trenger ha med historikk eller ikke..
            return httpClient.get<HttpStatement>("$baseUrl/v1/organisasjon/$organisasjonsnummer?inkluderHierarki=true&inkluderHistorikk=true") {
                header("Authorization", "Bearer ${stsRestClient.token()}")
                header("Nav-Consumer-Token", "Bearer ${stsRestClient.token()}")
                header("Nav-Consumer-Id", "spre-gosys")
                header("Nav-Call-Id", callId)
                accept(ContentType.Application.Json)
            }
                .execute { it.readText() }
                .let<String, JsonNode>(objectMapper::readValue)
                .let { response ->
                    EregResponse(
                        navn = trekkUtNavn(response),
                    )
                }
        } catch (exception: RuntimeException) {
            log.error("Feil ved henting av organiasasjonsnavn. Sjekk sikker logg for detaljer")
            sikkerLogg.error("Feil ved henting av organiasasjonsnavn orgnummer=$organisasjonsnummer", exception)
            throw exception
        }
    }

    private fun trekkUtNavn(organisasjon: JsonNode) =
        organisasjon["navn"].let { navn ->
            (1..5).mapNotNull { index -> navn["navnelinje$index"] }
                .filterNot(JsonNode::isMissingOrNull)
                .map(JsonNode::asText)
                .filterNot(String::isBlank)
        }.joinToString()

}

data class EregResponse(
    val navn: String,
)
