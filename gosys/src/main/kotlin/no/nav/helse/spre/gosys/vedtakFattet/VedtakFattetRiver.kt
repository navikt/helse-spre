package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.sikkerLogg
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetDao: VedtakFattetDao,
    private val utbetalingDao: UtbetalingDao,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val vedtakMediator: VedtakMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireAny("@event_name", listOf("vedtak_fattet", "republisert_vedtak_fattet"))
                it.requireKey("sykepengegrunnlagsfakta")
            }
            validate { message ->
                message.requireKey(
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "hendelser",
                    "sykepengegrunnlag",
                    "inntekt",
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver",
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("skjæringstidspunkt", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.interestedIn("utbetalingId") { id -> UUID.fromString(id.asText()) }
                message.interestedIn("begrunnelser")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                val utbetalingId = packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let { UUID.fromString(it.asText()) }
                val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
                log.info("vedtak_fattet leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

                val vedtakFattet = VedtakFattetData.fromJson(id, packet)

                if (utbetalingId != null && erLogiskDuplikat(utbetalingId, vedtaksperiodeId)) {
                    log.warn("har allerede behandlet vedtak_fattet for vedtaksperiode $vedtaksperiodeId og utbetaling $utbetalingId")
                    return@sjekkDuplikat
                }

                vedtakFattetDao.lagre(vedtakFattet, packet.toJson())
                log.info("vedtak_fattet lagret for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId på id $id")

                if (utbetalingId != null) {
                    utbetalingDao.finnUtbetalingData(utbetalingId)?.avgjørVidereBehandling(vedtakFattetDao, vedtakMediator)
                }
            }
        }  catch (err: Exception) {
            log.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i vedtak fattet-river: ${err.message}", err)
            throw err
        }
    }

    private fun erLogiskDuplikat(utbetalingId: UUID, vedtaksperiodeId: UUID?) =
        vedtakFattetDao.finnVedtakFattetData(utbetalingId).any { vedtak ->
            vedtak.vedtaksperiodeId == vedtaksperiodeId && vedtakFattetDao.erJournalført(vedtak)
        }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }
}
