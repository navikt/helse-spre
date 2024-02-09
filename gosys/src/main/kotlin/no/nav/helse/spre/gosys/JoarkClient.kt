package no.nav.helse.spre.gosys

import com.github.navikt.tbd_libs.azure.AzureTokenProvider
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import net.logstash.logback.argument.StructuredArguments.keyValue
import java.util.*
import javax.net.ssl.SSLHandshakeException

class JoarkClient(
    private val baseUrl: String,
    private val azureClient: AzureTokenProvider,
    private val joarkScope: String,
    private val httpClient: HttpClient
) {
    suspend fun opprettJournalpost(hendelseId: UUID, journalpostPayload: JournalpostPayload): Boolean {
        return httpClient.preparePost("$baseUrl/rest/journalpostapi/v1/journalpost?forsoekFerdigstill=true") {
            System.getenv("NAIS_APP_NAME")?.also { header("Nav-Consumer-Id", it) }
            header("Nav-Consumer-Token", hendelseId.toString())
            bearerAuth(azureClient.bearerToken(joarkScope).token)
            contentType(ContentType.Application.Json)
            setBody(journalpostPayload)
        }
            .executeRetry(avbryt = { it::class !in forsøkPåNy }) {
                if (it.status.value !in ((200..300) + 409)) {
                    val error = it.body<String>()
                    log.error("Feil fra Joark: {}", keyValue("response", error))
                    throw JoarkClientException("Feil fra Joark: $error")
                } else true
            }
    }
    internal companion object {
        internal class JoarkClientException(feil: String): RuntimeException(feil)
        private val forsøkPåNy = setOf(ClosedReceiveChannelException::class, SSLHandshakeException::class, HttpRequestTimeoutException::class, JoarkClientException::class)
    }
}

data class JournalpostPayload(
    val tittel: String,
    val journalpostType: String = "NOTAT",
    val tema: String = "SYK",
    val behandlingstema: String = "ab0061",
    val journalfoerendeEnhet: String = "9999",
    val bruker: Bruker,
    val sak: Sak = Sak(),
    val dokumenter: List<Dokument>,
    val eksternReferanseId: String,
) {
    data class Bruker(
        val id: String,
        val idType: String = "FNR"
    )

    data class Sak(
        val sakstype: String = "GENERELL_SAK"
    )

    data class Dokument(
        val tittel: String,
        val dokumentvarianter: List<DokumentVariant>

    ) {
        data class DokumentVariant(
            val filtype: String = "PDFA",
            val fysiskDokument: String,
            val variantformat: String = "ARKIV"
        )
    }
}
