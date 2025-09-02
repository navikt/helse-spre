package no.nav.helse.spre.gosys

import com.github.navikt.tbd_libs.result_object.fold
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.result_object.tryCatch
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.PersonResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.ContentType.Application.Json
import java.util.*
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.spre.gosys.annullering.AnnulleringPdfPayload
import no.nav.helse.spre.gosys.annullering.PlanlagtAnnullering
import no.nav.helse.spre.gosys.annullering.TomAnnulleringMessage
import no.nav.helse.spre.gosys.feriepenger.FeriepengerPdfPayload
import no.nav.helse.spre.gosys.vedtak.VedtakPdfPayload

class PdfClient(private val httpClient: HttpClient, private val baseUrl: String) {
    private val encoder = Base64.getEncoder()

    suspend fun hentVedtakPdf(vedtak: VedtakPdfPayload) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/vedtak", vedtak)

    suspend fun hentAnnulleringPdf(annullering: AnnulleringPdfPayload) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/annullering", annullering)

    suspend fun hentAnnulleringPdf(tomAnnullering: TomAnnulleringMessage.TomAnnulleringPdfPayload) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/annullering", tomAnnullering)

    suspend fun hentFerdigAnnulleringPdf(ferdigAnnullering: PlanlagtAnnullering.FerdigAnnulleringPdfPayload) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/ferdig-annullering", ferdigAnnullering)

    suspend fun hentFeriepengerPdf(feriepenger: FeriepengerPdfPayload) =
        hentPdf("$baseUrl/api/v1/genpdf/spre-gosys/feriepenger", feriepenger)

    private suspend fun hentPdf(url: String, input: Any): String =
        httpClient.preparePost(url) {
            contentType(Json)
            setBody(input)
            expectSuccess = true
        }.executeRetry { response ->
            response.body<ByteArray>().let(encoder::encodeToString).also { if (it.isNullOrBlank()) error("Fikk tom pdf") }
        }
}

suspend fun finnOrganisasjonsnavn(eregClient: EregClient, organisasjonsnummer: String, callId: UUID = UUID.randomUUID()): String {
    return try {
        eregClient.hentOrganisasjonsnavn(organisasjonsnummer, callId).navn
    } catch (e: Exception) {
        logg.error("Feil ved henting av bedriftsnavn for $organisasjonsnummer {}", kv("callId", callId))
        sikkerLogg.error("Feil ved henting av bedriftsnavn for $organisasjonsnummer {}", kv("callId", callId), e)
        ""
    }
}

fun hentNavn(speedClient: SpeedClient, ident: String, callId: String) =
    tryCatch {
        retryBlocking {
            speedClient.hentPersoninfo(ident, callId).getOrThrow()
        }
    }
        .fold(
            whenOk = { it.tilVisning() },
            whenError = { msg, cause ->
                logg.error("Feil ved henting av navn {}", kv("callId", callId))
                sikkerLogg.error("Feil ved henting av navn for ident=$ident: $msg {}", kv("callId", callId), cause)
                null
            }
        )

private fun PersonResponse.tilVisning() =
    listOfNotNull(fornavn, mellomnavn, etternavn)
        .map { it.lowercase() }
        .flatMap { it.split(" ") }
        .joinToString(" ") { navnebit -> navnebit.replaceFirstChar { it.uppercase() } }
