package no.nav.helse.spre.gosys.vedtaksperiodeForkastet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.EregClient
import no.nav.helse.spre.gosys.JoarkClient
import no.nav.helse.spre.gosys.JournalpostPayload
import no.nav.helse.spre.gosys.PdfClient
import no.nav.helse.spre.gosys.annullering.AnnulleringDao
import no.nav.helse.spre.gosys.erUtvikling
import no.nav.helse.spre.gosys.finnOrganisasjonsnavn
import no.nav.helse.spre.gosys.hentNavn
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.VedtakMessage

internal class VedtaksperiodeForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingDao: UtbetalingDao,
    private val annulleringDao: AnnulleringDao,
    private val pdfClient: PdfClient,
    private val joarkClient: JoarkClient,
    private val eregClient: EregClient,
    private val speedClient: SpeedClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "vedtaksperiode_forkastet")
                it.requireValue("tilstand", "TIL_ANNULLERING")
            }
            validate { message ->
                message.requireKey(
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "yrkesaktivitetstype"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error("forstod ikke vedtaksperiode_forkastet. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke vedtakeperiode_forkastet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
            withMDC(
                mapOf(
                    "meldingsreferanseId" to "$id",
                    "vedtaksperiodeId" to "$vedtaksperiodeId",
                )
            ) {
                behandleMelding(id, vedtaksperiodeId, packet)
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i vedtaksperiode forkastet-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtaksperiode forkastet-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(meldingId: UUID, vedtaksperiodeId: UUID, packet: JsonMessage) {
        logg.info("vedtaksperiode_forkastet leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")
        val vedtaksperiodeForkastetMessage = VedtaksperiodeForkastetMessage(
            hendelseId = UUID.fromString(packet["@id"].asText()),
            vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText()),
            fødselsnummer = packet["fødselsnummer"].asText(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            organisasjonsnummer = packet["organisasjonsnummer"].asText()
        )

        val utbetalinger = utbetalingDao.finnUtbetalingData(vedtaksperiodeForkastetMessage.fødselsnummer, vedtaksperiodeForkastetMessage.organisasjonsnummer)
        if (utbetalinger.isEmpty()) return

        val sisteUtbetalingSomOverlapper = utbetalinger.filter { it.fom <= vedtaksperiodeForkastetMessage.fom && it.tom >= vedtaksperiodeForkastetMessage.tom }.maxByOrNull { it.opprettet }
        if (sisteUtbetalingSomOverlapper == null) return

        val relevanteUtbetalinger = utbetalinger.filter { it.arbeidsgiverOppdrag.fagsystemId == sisteUtbetalingSomOverlapper.arbeidsgiverOppdrag.fagsystemId }
        if (relevanteUtbetalinger.size == 1) return // Hvis det kun er én utbetaling med fagsystemId, "eier" perioden utbetalingen, og den er allerede blitt annullert

        val revurderingsutbetaling = relevanteUtbetalinger.maxByOrNull { it.opprettet }
        revurderingsutbetaling?.let {
            val erRevurderingUtbetalingFørVedtaksperiodeForkastetMessage = revurderingsutbetaling.tom < vedtaksperiodeForkastetMessage.fom
            val harNegativNetto = revurderingsutbetaling.arbeidsgiverOppdrag.nettoBeløp < 0 || revurderingsutbetaling.personOppdrag.nettoBeløp < 0
            if(erRevurderingUtbetalingFørVedtaksperiodeForkastetMessage && it.erRevurdering() && harNegativNetto) {
                  val h = "HER SKAL VI LAGE GOSYSNOTATER"
            }
        }

    }

    private fun lagPdf(organisasjonsnummer: String, fødselsnummer: String, vedtak: VedtakMessage): String {
        val organisasjonsnavn = runBlocking { finnOrganisasjonsnavn(eregClient, organisasjonsnummer) }
        logg.debug("Hentet organisasjonsnavn")
        val navn = hentNavn(speedClient, fødselsnummer, UUID.randomUUID().toString()) ?: ""
        logg.debug("Hentet søkernavn")

        val vedtakPdfPayload = vedtak.toVedtakPdfPayload(organisasjonsnavn, navn)
        if (erUtvikling) sikkerLogg.info("vedtak-payload: ${objectMapper.writeValueAsString(vedtakPdfPayload)}")
        return runBlocking { pdfClient.hentVedtakPdf(vedtakPdfPayload) }
    }

    private fun journalførPdf(vedtak: VedtakMessage, journalpostPayload: JournalpostPayload): Boolean {
        return runBlocking { joarkClient.opprettJournalpost(vedtak.utbetalingId, journalpostPayload) }
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
