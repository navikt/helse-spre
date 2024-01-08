package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.rapids_rivers.isMissingOrNull
import java.util.*

class EregClient(
    private val baseUrl: String,
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
            sikkerLogg.info("Henter navn på organisasjon: $organisasjonsnummer, {}", kv("Nav-Call-Id", callId))
            return httpClient.prepareGet("$baseUrl/v1/organisasjon/$organisasjonsnummer") {
                System.getenv("NAIS_APP_NAME")?.also { header("Nav-Consumer-Id", it) }
                header("Nav-Call-Id", callId)
                accept(ContentType.Application.Json)
            }
                .execute { it.bodyAsText() }
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
