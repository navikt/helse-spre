package no.nav.helse.spre.gosys.vedtak

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import java.time.LocalDate

class VedtakMediator(private val pdfClient: PdfClient, private val joarkClient: JoarkClient) {
    internal fun opprettVedtak(vedtakMessage: VedtakMessage) {
        if (vedtakMessage.type == Utbetaling.Utbetalingtype.ANNULLERING) return //Annullering har eget notat
        runBlocking {
            val pdf = pdfClient.hentVedtakPdf(vedtakMessage.toVedtakPdfPayload())
            val journalpostPayload = JournalpostPayload(
                tittel = journalpostTittel(vedtakMessage.type),
                bruker = JournalpostPayload.Bruker(id = vedtakMessage.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = dokumentTittel(vedtakMessage),
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                )
            )
            joarkClient.opprettJournalpost(vedtakMessage.hendelseId, journalpostPayload).let { success ->
                if (success) log.info("Vedtak journalført for aktør: ${vedtakMessage.aktørId}")
                else log.warn("Feil oppstod under journalføring av vedtak")
            }
        }
    }

    private fun dokumentTittel(vedtakMessage: VedtakMessage): String {
        return when(vedtakMessage.type){
            Utbetaling.Utbetalingtype.UTBETALING -> "Sykepenger behandlet i ny løsning, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Sykepenger etterutbetalt i ny løsning, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.REVURDERING -> "Sykepenger revurdert i ny løsning, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }


    private fun journalpostTittel(type: Utbetaling.Utbetalingtype): String {
        return when (type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "Vedtak om sykepenger"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Vedtak om etterutbetaling av sykepenger"
            Utbetaling.Utbetalingtype.REVURDERING -> "Vedtak om revurdering av sykepenger"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }
}
