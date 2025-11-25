package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.toUUID
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import kotliquery.TransactionalSession
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.journalfør
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfJournalfører
import no.nav.helse.spre.gosys.vedtakFattet.pdf.PdfProduserer

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val meldingOmVedtakRepository: MeldingOmVedtakRepository,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val pdfProduserer: PdfProduserer,
    private val pdfJournalfører: PdfJournalfører,
    private val sessionFactory: SessionFactory,
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
        sessionFactory.transactionally {
            val id = UUID.fromString(packet["@id"].asText())
            if (duplikatsjekkDao.erDuplikat(id)) {
                logg.info("Hopper over $EVENT_NAME-melding $id som allerede er behandlet (duplikatsjekk)")
                return
            }

            val utbetalingId = packet["utbetalingId"].asText().toUUID()
            val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
            logg.info("$EVENT_NAME leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

            if (erDuplikatBehandling(utbetalingId, vedtaksperiodeId))
                return logg.warn("har allerede behandlet $EVENT_NAME for vedtaksperiode $vedtaksperiodeId og utbetaling $utbetalingId")

            val meldingOmVedtak = MeldingOmVedtak(
                id = id,
                utbetalingId = utbetalingId,
                fødselsnummer = packet["fødselsnummer"].asText(),
                journalførtTidspunkt = null,
                json = packet.toJson()
            )
            meldingOmVedtakRepository.lagre(meldingOmVedtak)
            logg.info("$EVENT_NAME lagret for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId på id $id")

            val utbetaling = utbetalingDao.finnUtbetalingData(utbetalingId) ?: return logg.info("Melding om vedtak lagret, venter på utbetaling")

            journalfør(
                meldingId = id,
                utbetaling = utbetaling,
                meldingOmVedtak = meldingOmVedtak,
                pdfProduserer = pdfProduserer,
                pdfJournalfører = pdfJournalfører,
                duplikatsjekkDao = duplikatsjekkDao,
                meldingOmVedtakRepository = meldingOmVedtakRepository,
            )
        }
    }

    context(session: TransactionalSession)
    private fun erDuplikatBehandling(utbetalingId: UUID, vedtaksperiodeId: UUID): Boolean {
        val tidligereMeldingOmVedtak = meldingOmVedtakRepository.finn(utbetalingId) ?: return false
        val tidligereMeldingOmVedtakVedtaksperiodeId = objectMapper.readTree(tidligereMeldingOmVedtak.json)["vedtaksperiodeId"].asText().let { UUID.fromString(it) }

        check(vedtaksperiodeId == tidligereMeldingOmVedtakVedtaksperiodeId) {
            "det finnes et tidligere vedtak (vedtaksperiode ${tidligereMeldingOmVedtakVedtaksperiodeId}) " +
                "med samme utbetalingId, som er ulik vedtaksperiode $vedtaksperiodeId"
        }

        return tidligereMeldingOmVedtak.erJournalført()
    }

    companion object {
        private const val EVENT_NAME = "vedtak_fattet"
    }
}
