package no.nav.helse.spre.gosys.feriepenger

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.log

class FeriepengerMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    fun opprettFeriepenger(feriepenger: FeriepengerMessage) {
        duplikatsjekkDao.sjekkDuplikat(feriepenger.hendelseId) {
            runBlocking {
                val pdf = pdfClient.hentFeriepengerPdf(FeriepengerPdfPayload("Feriepenger utbetalt for sykepenger", emptyList(), null))
                val journalpostPayload = JournalpostPayload(
                    tittel = "Feriepenger utbetalt for sykepenger",
                    bruker = JournalpostPayload.Bruker(id = feriepenger.fødselsnummer),
                    dokumenter = listOf(
                        JournalpostPayload.Dokument(
                            tittel = "Utbetaling annullert i ny løsning ${feriepenger.norskFom} - ${feriepenger.norskTom}",
                            dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                        )
                    )
                )
                if (joarkClient.opprettJournalpost(feriepenger.hendelseId, journalpostPayload)) {
                    log.info("Annullering journalført for aktør: ${feriepenger.aktørId}")
                } else {
                    log.warn("Feil oppstod under journalføring av annullering")
                }
            }
        }
    }

}
