package no.nav.helse.spre.gosys

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import no.nav.helse.spre.gosys.annullering.AnnulleringPdfPayloadV2
import no.nav.helse.spre.gosys.feriepenger.FeriepengerPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2
import java.util.*

class PdfClient(private val httpClient: HttpClient, private val baseUrl: String) {
    private val encoder = Base64.getEncoder()

    suspend fun hentVedtakPdfV2(vedtak: VedtakPdfPayloadV2) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/vedtak-v2", vedtak)

    suspend fun hentAnnulleringPdf(annullering: AnnulleringPdfPayloadV2) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/annullering-v2", annullering)

    suspend fun hentFeriepengerPdf(feriepenger: FeriepengerPdfPayload) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/feriepenger", feriepenger)

    private suspend fun hentPdf(url: String, input: Any) =
        httpClient.preparePost(url) {
            contentType(Json)
            setBody(input)
        }.executeRetry { response ->
            response.body<ByteArray>().let(encoder::encodeToString).also { if (it.isNullOrBlank()) error("Fikk tom pdf") }
        }
}
