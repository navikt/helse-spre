package no.nav.helse.spre.gosys

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import no.nav.helse.spre.gosys.annullering.AnnulleringPdfPayload
import no.nav.helse.spre.gosys.feriepenger.FeriepengerPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import java.util.Base64

class PdfClient(private val httpClient: HttpClient) {
    private val encoder = Base64.getEncoder()
    suspend fun hentVedtakPdf(vedtak: VedtakPdfPayload) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak", vedtak)

    suspend fun hentVedtakPdfV2(vedtak: VedtakPdfPayload) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak-v2", vedtak)

    suspend fun hentAnnulleringPdf(annullering: AnnulleringPdfPayload) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/annullering", annullering)
    suspend fun hentFeriepengerPdf(feriepenger: FeriepengerPdfPayload) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/feriepenger", feriepenger)

    private suspend fun hentPdf(url: String, input: Any) =
        httpClient.post<ByteArray>(url) {
        contentType(Json)
        body = input
    }.let(encoder::encodeToString)
        .also { if (it.isNullOrBlank()) error("Fikk tom pdf") }
}
