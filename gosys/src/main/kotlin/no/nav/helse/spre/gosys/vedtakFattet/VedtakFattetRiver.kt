package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.Utbetaling
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfJournalfører
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfProduserer

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetDao: VedtakFattetDao,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val pdfProduserer: PdfProduserer,
    private val pdfJournalfører: PdfJournalfører,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireKey("sykepengegrunnlagsfakta")
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
            }
            validate { message ->
                message.requireKey(
                    "@id",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "sykepengegrunnlag"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("skjæringstidspunkt", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.require("vedtakFattetTidspunkt", JsonNode::asLocalDateTime)
                message.interestedIn(
                    "begrunnelser",
                    "organisasjonsnummer",
                    "yrkesaktivitetstype",
                )
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error("forstod ikke $EVENT_NAME. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke $EVENT_NAME:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        if (duplikatsjekkDao.erDuplikat(id)) {
            logg.info("Hopper over $EVENT_NAME-melding $id som allerede er behandlet (duplikatsjekk)")
            return
        }

        val utbetalingId = packet["utbetalingId"].asText().toUUID()
        val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
        withMDC(
            mapOf(
                "meldingsreferanseId" to "$id",
                "vedtaksperiodeId" to "$vedtaksperiodeId",
                "utbetalingId" to "$utbetalingId"
            )
        ) {
            behandleMelding(
                packet = packet,
                meldingId = id,
                utbetalingId = utbetalingId,
                vedtaksperiodeId = vedtaksperiodeId
            )
        }

        duplikatsjekkDao.insertTilDuplikatsjekk(id)
    }

    private fun behandleMelding(
        packet: JsonMessage,
        meldingId: UUID,
        utbetalingId: UUID,
        vedtaksperiodeId: UUID
    ) {
        logg.info("$EVENT_NAME leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

        if (erDuplikatBehandling(utbetalingId, vedtaksperiodeId))
            return logg.warn("har allerede behandlet $EVENT_NAME for vedtaksperiode $vedtaksperiodeId og utbetaling $utbetalingId")

        vedtakFattetDao.lagre(
            id = meldingId,
            utbetalingId = utbetalingId,
            fødselsnummer = packet["fødselsnummer"].asText(),
            json = packet.toJson()
        )
        logg.info("$EVENT_NAME lagret for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId på id $meldingId")

        val utbetaling = checkNotNull(utbetalingDao.finnUtbetalingData(utbetalingId)) {
            "forventer å finne utbetaling for vedtak for $vedtaksperiodeId"
        }
        check(utbetaling.type in setOf(Utbetaling.Utbetalingtype.UTBETALING, Utbetaling.Utbetalingtype.REVURDERING)) {
            "vedtaket for $vedtaksperiodeId peker på utbetalingtype ${utbetaling.type}. Forventer kun Utbetaling/Revurdering"
        }
        // justerer perioden ved å hoppe over AIG-dager i snuten (todo: gjøre dette i spleis?)
        val (søknadsperiodeFom, søknadsperiodeTom) = utbetaling.søknadsperiode(packet["fom"].asLocalDate() to packet["tom"].asLocalDate())

        val pdfBytes = pdfProduserer.lagPdf(
            packet = packet,
            utbetaling = utbetaling,
            søknadsperiodeFom = søknadsperiodeFom,
            søknadsperiodeTom = søknadsperiodeTom
        )

        pdfJournalfører.journalførPdf(
            pdfBytes = pdfBytes,
            vedtakFattetMeldingId = meldingId,
            utbetaling = utbetaling,
            søknadsperiodeFom = søknadsperiodeFom,
            søknadsperiodeTom = søknadsperiodeTom
        )
    }

    private fun erDuplikatBehandling(utbetalingId: UUID, vedtaksperiodeId: UUID): Boolean {
        val tidligereVedtakFattet = vedtakFattetDao.finnVedtakFattetData(utbetalingId) ?: return false

        check(tidligereVedtakFattet.vedtaksperiodeId == vedtaksperiodeId) {
            "det finnes et tidligere vedtak (vedtaksperiode ${tidligereVedtakFattet.vedtaksperiodeId}) " +
                "med samme utbetalingId, som er ulik vedtaksperiode $vedtaksperiodeId"
        }

        return vedtakFattetDao.erJournalført(tidligereVedtakFattet.id)
    }

    companion object {
        private const val EVENT_NAME = "vedtak_fattet"
    }
}
