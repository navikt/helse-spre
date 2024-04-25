package no.nav.helse.spre.styringsinfo.teamsak.enhet

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asOptionalLocalDate
import no.nav.helse.spre.styringsinfo.objectMapper
import no.nav.helse.spre.styringsinfo.sikkerLogg
import java.lang.Exception
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.LocalDate

internal class NavOrganisasjonsmasterClient(private val baseUrl: String, private val scope: String, private val azureClient: AzureTokenProvider) {

    companion object {
        private const val dollar = '$'

        internal fun JsonNode.enhet(gyldigPåDato: LocalDate): String? {
            val tilknytninger = this["data"]["ressurs"]["orgTilknytning"].map {
                Tilknytning(
                    gyldigFom = it["gyldigFom"].asLocalDate(),
                    gyldigTom = it["gyldigTom"].asOptionalLocalDate(),
                    orgEnhetsType = it["orgEnhet"]["orgEnhetsType"].asText(),
                    enhet = it["orgEnhet"]["remedyEnhetId"].asText()
                )
            }
            return tilknytninger
                .filter { it.gyldigFom <= gyldigPåDato && (it.gyldigTom == null || it.gyldigTom >= gyldigPåDato) }
                .sortedWith { kandidat, _ ->
                    when (kandidat.orgEnhetsType) {
                        "NAV_ARBEID_OG_YTELSER" -> -1
                        else -> 0
                    }
                }.firstOrNull()?.enhet
        }

        private data class Tilknytning(
            val gyldigFom: LocalDate,
            val gyldigTom: LocalDate?,
            val orgEnhetsType: String,
            val enhet: String
        )
    }
    internal fun hentEnhet(ident: String, gyldigPåDato: LocalDate, hendelseId: String): Enhet {
        try {
            val accessToken = azureClient.bearerToken(scope).token

            val body =
                objectMapper.writeValueAsString(
                    NomQuery(query = finnEnhetQuery.onOneLine(), variables = Variables(ident))
                )

            val request = HttpRequest.newBuilder(URI.create("$baseUrl/graphql"))
                .header("Authorization", "Bearer $accessToken")
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("Nav-Call-Id", hendelseId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build()

            val newHttpClient = HttpClient.newHttpClient()
            val responseHandler = HttpResponse.BodyHandlers.ofString()
            val response = newHttpClient.send(request, responseHandler)

            if (response.statusCode() != 200) {
                throw RuntimeException("error (responseCode=${response.statusCode()}) from NOM")
            }
            val responseBody = objectMapper.readTree(response.body())
            if (responseBody.containsErrors()) {
                throw RuntimeException("errors from NOM: ${responseBody["errors"].errorMsgs()}")
            }
            return responseBody.enhet(gyldigPåDato = gyldigPåDato)?.let { FunnetEnhet(it) } ?: ManglendeEnhet
        } catch (exception: Exception) {
            sikkerLogg.error("Feil oppsto ved kall mot NOM for ident $ident og hendelse $hendelseId", exception)
            return ManglendeEnhet
        }
    }

    private data class NomQuery(
        val query: String,
        val variables: Variables
    )

    private data class Variables(
        val navIdent: String
    )

    private fun String.onOneLine() = this.replace("\n", " ")
    private fun JsonNode.containsErrors() = this.has("errors")
    private fun JsonNode.errorMsgs() = with (this as ArrayNode) {
        val errorMsgs = this.map { it["message"]?.asText() ?: "unknown error" }
        val extensions = this.map { it["extensions"]?.get("details")?.asText() ?: "extension details unknown" }
        "$errorMsgs -- $extensions"
    }

    private val finnEnhetQuery: String = """
    query enhet(${dollar}navIdent: String!) {
      ressurs(where: {navident: ${dollar}navIdent}) {
          navident
          orgTilknytning {
              gyldigFom
              gyldigTom
              orgEnhet {
                  id
                  navn
                  remedyEnhetId
                  orgEnhetsType
              }
          }
      }
    }
    """.trimIndent()
}