package no.nav.helse.spre.gosys

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import no.nav.helse.spre.gosys.annullering.AnnulleringPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import java.util.Base64

class PdfClient(private val httpClient: HttpClient) {
    suspend fun hentVedtakPdf(vedtak: VedtakPdfPayload): String =
        httpClient.post<ByteArray>("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak") {
            contentType(Json)
            body = vedtak
        }.toPdfString()
            .also { if (it.isNullOrBlank()) error("Fikk tom pdf") }

    suspend fun hentAnnulleringPdf(annullering: AnnulleringPdfPayload): String =
        httpClient.post<ByteArray>("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/annullering") {
            contentType(Json)
            body = annullering
        }.toPdfString()
            .also { if (it.isNullOrBlank()) error("Fikk tom pdf") }

    private fun ByteArray.toPdfString() = Base64.getEncoder().encodeToString(this)
}
