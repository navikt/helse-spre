package no.nav.helse.spre.gosys.feriepenger

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import java.time.format.DateTimeFormatter

class FeriepengerMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val duplikatsjekkDao: DuplikatsjekkDao
) {
    fun opprettFeriepenger(feriepenger: FeriepengerMessage) {
        duplikatsjekkDao.sjekkDuplikat(feriepenger.hendelseId) {
            runBlocking {
                val pdf = pdfClient.hentFeriepengerPdf(FeriepengerPdfPayload(
                    tittel = "Feriepenger utbetalt for sykepenger",
                    oppdrag = feriepenger.oppdrag,
                    utbetalt = feriepenger.utbetalt,
                    orgnummer = feriepenger.orgnummer,
                    fødselsnummer = feriepenger.fødselsnummer
                ))

                val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
                val fom = feriepenger.oppdrag.map { it.fom }.minOrNull()!!
                val tom = feriepenger.oppdrag.map { it.tom }.maxOrNull()!!

                val journalpostPayload = JournalpostPayload(
                    tittel = "Feriepenger utbetalt for sykepenger",
                    bruker = JournalpostPayload.Bruker(id = feriepenger.fødselsnummer),
                    dokumenter = listOf(
                        JournalpostPayload.Dokument(
                            tittel = "Utbetaling av feriepenger i ny løsning ${fom.format(formatter)} - ${tom.format(formatter)}",
                            dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                        )
                    )
                )
                if (joarkClient.opprettJournalpost(feriepenger.hendelseId, journalpostPayload)) {
                    log.info("Feriepenger journalført for aktør: ${feriepenger.aktørId}")
                } else {
                    log.warn("Feil oppstod under journalføring av feriepenger")
                }
            }
        }
    }

}
