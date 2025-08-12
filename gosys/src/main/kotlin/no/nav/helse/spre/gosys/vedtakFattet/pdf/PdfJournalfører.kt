package no.nav.helse.spre.gosys.vedtakFattet.pdf

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao

class PdfJournalfører(
    private val vedtakFattetDao: VedtakFattetDao,
    private val joarkClient: JoarkClient
) {
    private val encoder = Base64.getEncoder()

    fun journalførPdf(
        pdfBytes: ByteArray,
        vedtakFattetMeldingId: UUID,
        utbetaling: Utbetaling,
        søknadsperiodeFom: LocalDate,
        søknadsperiodeTom: LocalDate
    ) {
        val journalpostPayload = JournalpostPayload(
            tittel = journalpostTittel(utbetaling.type),
            bruker = JournalpostPayload.Bruker(id = utbetaling.fødselsnummer),
            dokumenter = listOf(
                JournalpostPayload.Dokument(
                    tittel = dokumentTittel(type = utbetaling.type, fom = søknadsperiodeFom, tom = søknadsperiodeTom),
                    dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdfBytes.let(encoder::encodeToString)))
                )
            ),
            eksternReferanseId = utbetaling.utbetalingId.toString(),
        )

        if (!journalførPdf(utbetaling.utbetalingId, journalpostPayload)) return logg.warn("Feil oppstod under journalføring av vedtak")

        vedtakFattetDao.journalfør(vedtakFattetMeldingId)

        logg.info("Vedtak journalført for utbetalingId: ${utbetaling.utbetalingId}")
        sikkerLogg.info("Vedtak journalført for fødselsnummer=${utbetaling.fødselsnummer} utbetalingId: ${utbetaling.utbetalingId}")
    }

    private fun journalførPdf(
        utbetalingId: UUID,
        journalpostPayload: JournalpostPayload
    ): Boolean = runBlocking { joarkClient.opprettJournalpost(utbetalingId, journalpostPayload) }

    private fun dokumentTittel(
        type: Utbetaling.Utbetalingtype,
        fom: LocalDate,
        tom: LocalDate
    ): String = when (type) {
        Utbetaling.Utbetalingtype.UTBETALING -> "Sykepenger behandlet, ${fom.somNorskDato()} - ${tom.somNorskDato()}"
        Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Sykepenger etterutbetalt, ${fom.somNorskDato()} - ${tom.somNorskDato()}"
        Utbetaling.Utbetalingtype.REVURDERING -> "Sykepenger revurdert, ${fom.somNorskDato()} - ${tom.somNorskDato()}"
        Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
    }

    private fun LocalDate.somNorskDato(): String = format(DateTimeFormatter.ofPattern("dd.MM.yyyy"))

    private fun journalpostTittel(type: Utbetaling.Utbetalingtype): String {
        return when (type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "Vedtak om sykepenger"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Vedtak om etterutbetaling av sykepenger"
            Utbetaling.Utbetalingtype.REVURDERING -> "Vedtak om revurdering av sykepenger"
            Utbetaling.Utbetalingtype.ANNULLERING -> throw IllegalArgumentException("Forsøkte å opprette vedtaksnotat for annullering")
        }
    }
}
