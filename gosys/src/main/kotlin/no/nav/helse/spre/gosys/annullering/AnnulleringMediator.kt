package no.nav.helse.spre.gosys.annullering

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.pdl.PdlClient

class AnnulleringMediator(
    private val pdfClient: PdfClient,
    private val eregClient: EregClient,
    private val joarkClient: JoarkClient,
    private val pdlClient: PdlClient,
) {
    fun opprettAnnullering(annulleringMessage: AnnulleringMessage) {
        runBlocking {
            val organisasjonsnavn: String? = try {
                eregClient.hentOrganisasjonsnavn(
                    annulleringMessage.organisasjonsnummer,
                    annulleringMessage.hendelseId
                ).navn
            } catch (e: Exception) {
                log.error("Feil ved henting av bedriftsnavn")
                null
            }
            val navn = try {
                pdlClient.hentPersonNavn(annulleringMessage.fødselsnummer, annulleringMessage.hendelseId)
            } catch (e: Exception) {
                log.error("Feil ved henting av navn for ${annulleringMessage.aktørId}")
                sikkerLogg.error("Feil ved henting av navn for ${annulleringMessage.aktørId}", e)
                null
            }
            val pdf = pdfClient.hentAnnulleringPdf(annulleringMessage.toPdfPayloadV2(organisasjonsnavn, navn))


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
