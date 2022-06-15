package no.nav.helse.spre.gosys

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.http.ContentType.Application.Json
import io.ktor.http.contentType
import no.nav.helse.spre.gosys.annullering.AnnulleringPdfPayloadV2
import no.nav.helse.spre.gosys.feriepenger.FeriepengerPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayloadV2
import java.util.Base64

class PdfClient(private val httpClient: HttpClient) {
    private val encoder = Base64.getEncoder()
    suspend fun hentVedtakPdf(vedtak: VedtakPdfPayload) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak", vedtak)

    suspend fun hentVedtakPdfV2(vedtak: VedtakPdfPayloadV2) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/vedtak-v2", vedtak)

    suspend fun hentAnnulleringPdf(annullering: AnnulleringPdfPayloadV2) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/annullering-v2", annullering)

    suspend fun hentFeriepengerPdf(feriepenger: FeriepengerPdfPayload) =
        hentPdf("http://spre-gosys-pdf.tbd.svc.nais.local/api/v1/genpdf/spre-gosys/feriepenger", feriepenger)

    private suspend fun hentPdf(url: String, input: Any) =
        httpClient.post(url) {
        contentType(Json)
        setBody(input)
    }.body<ByteArray>() .let(encoder::encodeToString)
        .also { if (it.isNullOrBlank()) error("Fikk tom pdf") }
}
