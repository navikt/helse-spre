package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.utbetaling.UtbetalingDao
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("spregosys")
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
            validate { message ->
                message.demandValue("@event_name", "vedtak_fattet")
                message.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "vedtaksperiodeId",
                    "organisasjonsnummer",
                    "hendelser",
                    "sykepengegrunnlag",
                    "inntekt",
                    "@forårsaket_av.event_name",
                    "grunnlagForSykepengegrunnlagPerArbeidsgiver"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("skjæringstidspunkt", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.interestedIn("utbetalingId") { id -> UUID.fromString(id.asText()) }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        duplikatsjekkDao.sjekkDuplikat(id) {
            val utbetalingId =
                packet["utbetalingId"].takeUnless(JsonNode::isMissingOrNull)?.let { UUID.fromString(it.asText()) }
            val vedtaksperiodeId = UUID.fromString(packet["vedtaksperiodeId"].asText())
            log.info("vedtak_fattet leses inn for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId")

            val vedtakFattet = VedtakFattetData.fromJson(id, packet)
            vedtakFattetDao.lagre(vedtakFattet, packet.toJson())
            log.info("vedtak_fattet lagret for vedtaksperiode med vedtaksperiodeId $vedtaksperiodeId på id $id")

            if (utbetalingId != null) {
                utbetalingDao.finnUtbetalingData(utbetalingId)
                    ?.avgjørVidereBehandling(vedtakFattetDao, vedtakMediator)
            }
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }
}
