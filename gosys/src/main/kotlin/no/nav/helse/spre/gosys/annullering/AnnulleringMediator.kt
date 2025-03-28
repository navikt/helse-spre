package no.nav.helse.spre.gosys.annullering

import com.github.navikt.tbd_libs.result_object.fold
import com.github.navikt.tbd_libs.result_object.getOrThrow
import com.github.navikt.tbd_libs.result_object.tryCatch
import com.github.navikt.tbd_libs.retry.retryBlocking
import com.github.navikt.tbd_libs.speed.PersonResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import kotlinx.coroutines.runBlocking
import net.logstash.logback.argument.StructuredArguments.kv
import no.nav.helse.spre.gosys.*

class AnnulleringMediator(
    private val pdfClient: PdfClient,
    private val eregClient: EregClient,
    private val joarkClient: JoarkClient,
    private val speedClient: SpeedClient,
) {
    fun opprettAnnullering(annulleringMessage: AnnulleringMessage) {
        runBlocking {
            val organisasjonsnavn: String? = try {
                eregClient.hentOrganisasjonsnavn(
                    annulleringMessage.organisasjonsnummer,
                    annulleringMessage.hendelseId
                ).navn
            } catch (e: Exception) {
                logg.error("Feil ved henting av bedriftsnavn")
                null
            }

            val navn = hentNavn(speedClient, annulleringMessage.fødselsnummer, annulleringMessage.hendelseId.toString())
            val pdf = pdfClient.hentAnnulleringPdf(annulleringMessage.toPdfPayloadV2(organisasjonsnavn, navn))

            val journalpostPayload = JournalpostPayload(
                tittel = "Annullering av vedtak om sykepenger",
                bruker = JournalpostPayload.Bruker(id = annulleringMessage.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = "Utbetaling annullert i ny løsning ${annulleringMessage.norskFom} - ${annulleringMessage.norskTom}",
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                ),
                eksternReferanseId = annulleringMessage.utbetalingId.toString(),
            )
            joarkClient.opprettJournalpost(annulleringMessage.hendelseId, journalpostPayload).let { success ->
                if (success) logg.info("Annullering journalført for hendelseId=${annulleringMessage.hendelseId}")
                else logg.warn("Feil oppstod under journalføring av annullering")
            }
        }
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
