package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import com.github.navikt.tbd_libs.result_object.getOrThrow
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import no.nav.helse.spre.gosys.vedtak.Dekning
import java.util.UUID
import no.nav.helse.spre.gosys.vedtak.Forsikringsvurdering

class SpForsikringClient(
    private val baseUrl: String,
    private val azureClient: AzureTokenProvider,
    private val scope: String,
    private val httpClient: HttpClient,
) {
    suspend fun hentForsikringsvurdering(forsikringsvurderingId: UUID): Forsikringsvurdering? {
        val callId = UUID.randomUUID()
        logg.info("Henter forsikringsvurdering for id $forsikringsvurderingId")
        val response = httpClient.get("$baseUrl/forsikringsvurderinger/$forsikringsvurderingId") {
            header("callId", callId)
            bearerAuth(azureClient.bearerToken(scope).getOrThrow().token)
            accept(ContentType.Application.Json)
        }
        return when (response.status.value) {
            200 -> {
                val json = objectMapper.readValue<JsonNode>(response.bodyAsText())
                json["dekning"]?.takeUnless { it.isNull }?.let { dekning ->
                    Forsikringsvurdering(
                        dekning = Dekning(
                            dekningsgrad = dekning["grad"].asInt(),
                            gjelderFraDag = dekning["fraDag"].asInt(),
                        ),
                        forsikringskategori = json["forsikringskategori"].asText(),
                    )
                }
            }
            404 -> {
                logg.warn("Fant ikke forsikringsvurdering med id $forsikringsvurderingId")
                null
            }
            else -> {
                logg.error("Feil ved henting av forsikringsvurdering: status=${response.status.value}")
                error("Feil fra sp-forsikring: ${response.status.value}")
            }
        }
    }
}
