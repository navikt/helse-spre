package no.nav.helse.spre.gosys.annullering

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.Toggle
import no.nav.helse.spre.gosys.*

class AnnulleringMediator(
    private val pdfClient: PdfClient,
    private val eregClient: EregClient,
    private val joarkClient: JoarkClient
) {
    fun opprettAnnullering(annulleringMessage: AnnulleringMessage) {
        runBlocking {
            val pdf =
                if (Toggle.AnnulleringTemplateV2.enabled) {
                    val organisasjonsnavn:String? = try {
                        eregClient.hentOrganisasjonsnavn(annulleringMessage.organisasjonsnummer, annulleringMessage.hendelseId).navn
                    } catch (e: Exception) {
                        log.error("Feil ved henting av bedriftsnavn")
                        null
                    }
                    pdfClient.hentAnnulleringPdf(annulleringMessage.toPdfPayloadV2(organisasjonsnavn))
            } else {
                    pdfClient.hentAnnulleringPdf(annulleringMessage.toPdfPayload())
            }

            val journalpostPayload = JournalpostPayload(
                tittel = "Annullering av vedtak om sykepenger",
                bruker = JournalpostPayload.Bruker(id = annulleringMessage.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = "Utbetaling annullert i ny løsning ${annulleringMessage.norskFom} - ${annulleringMessage.norskTom}",
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(annulleringMessage.hendelseId, journalpostPayload).let { success ->
                if (success) log.info("Annullering journalført for aktør: ${annulleringMessage.aktørId}")
                else log.warn("Feil oppstod under journalføring av annullering")
            }
        }
    }
}
