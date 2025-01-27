package no.nav.helse.spre.gosys.vedtak

import com.github.navikt.tbd_libs.speed.SpeedClient
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.*
import no.nav.helse.spre.gosys.annullering.hentNavn
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.vedtakFattet.Begrunnelse
import no.nav.helse.spre.gosys.vedtakFattet.SykepengegrunnlagsfaktaData
import java.time.LocalDate

class VedtakMediator(
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eregClient: EregClient,
    private val speedClient: SpeedClient
) {
    internal fun opprettSammenslåttVedtak(
        fom: LocalDate,
        tom: LocalDate,
        sykepengegrunnlag: Double,
        grunnlagForSykepengegrunnlag: Map<String, Double>,
        skjæringstidspunkt: LocalDate,
        sykepengegrunnlagsfakta: SykepengegrunnlagsfaktaData,
        begrunnelser: List<Begrunnelse>?,
        utbetaling: Utbetaling,
        klarteÅJournalføreCallback: () -> Unit
    ) {
        val (søknadsperiodeFom, søknadsperiodeTom) = utbetaling.søknadsperiode(fom to tom)
        val vedtak = VedtakMessage(søknadsperiodeFom, søknadsperiodeTom, sykepengegrunnlag, grunnlagForSykepengegrunnlag, skjæringstidspunkt, utbetaling, sykepengegrunnlagsfakta, begrunnelser)
        opprettSammenslåttVedtak(vedtak, klarteÅJournalføreCallback)
    }

    private fun opprettSammenslåttVedtak(vedtakMessage: VedtakMessage, klarteÅJournalføreCallback: () -> Unit) {
        if (vedtakMessage.type == Utbetaling.Utbetalingtype.ANNULLERING) return //Annullering har eget notat
        runBlocking {
            val organisasjonsnavn = try {
                eregClient.hentOrganisasjonsnavn(
                    vedtakMessage.organisasjonsnummer,
                    vedtakMessage.utbetalingId
                ).navn
            } catch (e: Exception) {
                logg.error("Feil ved henting av bedriftsnavn for ${vedtakMessage.organisasjonsnummer}")
                sikkerLogg.error("Feil ved henting av bedriftsnavn for ${vedtakMessage.organisasjonsnummer}, fødselsnummer=${vedtakMessage.fødselsnummer}")
                ""
            }
            logg.debug("Hentet organisasjonsnavn")
            val navn = hentNavn(speedClient, vedtakMessage.fødselsnummer, vedtakMessage.utbetalingId.toString()) ?: ""
            logg.debug("Hentet søkernavn")
            val vedtakPdfPayload = vedtakMessage.toVedtakPdfPayloadV2(organisasjonsnavn, navn)
            if (erUtvikling) sikkerLogg.info("vedtak-payload: ${objectMapper.writeValueAsString(vedtakPdfPayload)}")
            val pdf = pdfClient.hentVedtakPdfV2(vedtakPdfPayload)
            logg.debug("Hentet pdf")
            val journalpostPayload = JournalpostPayload(
                tittel = journalpostTittel(vedtakMessage.type),
                bruker = JournalpostPayload.Bruker(id = vedtakMessage.fødselsnummer),
                dokumenter = listOf(
                    JournalpostPayload.Dokument(
                        tittel = dokumentTittel(vedtakMessage),
                        dokumentvarianter = listOf(JournalpostPayload.Dokument.DokumentVariant(fysiskDokument = pdf))
                    )
                ),
                eksternReferanseId = vedtakMessage.utbetalingId.toString(),
            )
            val success = joarkClient.opprettJournalpost(vedtakMessage.utbetalingId, journalpostPayload)
            if (!success) {
                logg.warn("Feil oppstod under journalføring av vedtak")
                return@runBlocking
            }
            logg.info("Vedtak journalført for utbetalingId: ${vedtakMessage.utbetalingId}")
            sikkerLogg.info("Vedtak journalført for fødselsnummer=${vedtakMessage.fødselsnummer} utbetalingId: ${vedtakMessage.utbetalingId}")
            klarteÅJournalføreCallback()
        }
    }

    private fun dokumentTittel(vedtakMessage: VedtakMessage): String {
        return when (vedtakMessage.type) {
            Utbetaling.Utbetalingtype.UTBETALING -> "Sykepenger behandlet, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.ETTERUTBETALING -> "Sykepenger etterutbetalt, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
            Utbetaling.Utbetalingtype.REVURDERING -> "Sykepenger revurdert, ${vedtakMessage.norskFom} - ${vedtakMessage.norskTom}"
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
