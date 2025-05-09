package no.nav.helse.spre.gosys.feriepenger

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg
import java.time.format.DateTimeFormatter

class FeriepengerMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient
) {
    fun opprettFeriepenger(feriepenger: FeriepengerMessage) {
        runBlocking {
            val pdf = pdfClient.hentFeriepengerPdf(
                FeriepengerPdfPayload(
                    tittel = "Feriepenger utbetalt for sykepenger",
                    oppdrag = feriepenger.oppdrag,
                    utbetalt = feriepenger.utbetalt,
                    orgnummer = feriepenger.orgnummer,
                    fødselsnummer = feriepenger.fødselsnummer
                )
            )

            val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

            val journalpostPayload = JournalpostPayload(
                tittel = "Feriepenger utbetalt for sykepenger",
                bruker = JournalpostPayload.Bruker(id = feriepenger.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = "Utbetaling av feriepenger i ny løsning ${feriepenger.fom.format(formatter)} - ${feriepenger.tom.format(formatter)}",
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                ),
                eksternReferanseId = feriepenger.hendelseId.toString(),
            )
            if (joarkClient.opprettJournalpost(feriepenger.hendelseId, journalpostPayload)) {
                logg.info("Feriepenger journalført for hendelse: ${feriepenger.hendelseId}")
                sikkerLogg.info("Feriepenger journalført for hendelse: ${feriepenger.hendelseId} og fødselsnummer: ${feriepenger.fødselsnummer}")
            } else {
                logg.warn("Feil oppstod under journalføring av feriepenger")
            }
        }
    }

}
