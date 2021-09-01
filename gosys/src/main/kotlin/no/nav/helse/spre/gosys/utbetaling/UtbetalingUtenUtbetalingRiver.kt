package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.gosys.vedtak.VedtakMediator
import no.nav.helse.spre.gosys.vedtak.VedtakMessage.Companion.fraVedtakOgUtbetaling
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("spregosys")
private val sikkerLog = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingUtenUtbetalingRiver(
    rapidsConnection: RapidsConnection,
    private val utbetalingDao: UtbetalingDao,
    private val vedtakFattetDao: VedtakFattetDao,
    private val vedtakMediator: VedtakMediator
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_uten_utbetaling")
                it.requireKey(
                    "aktørId",
                    "fødselsnummer",
                    "@id",
                    "organisasjonsnummer",
                    "forbrukteSykedager",
                    "gjenståendeSykedager",
                    "automatiskBehandling",
                    "arbeidsgiverOppdrag",
                    "utbetalingsdager",
                    "type",
                    "ident"
                )
                it.require("vedtaksperiodeIder") { node ->
                    (node as ArrayNode).forEach { id ->
                        UUID.fromString(id.asText())
                    }
                }
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("maksdato", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("utbetalingId") { id -> UUID.fromString(id.asText()) }
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("forstod ikke utbetaling_utbetalt. (se sikkerlogg for melding)")
        sikkerLog.error("forstod ikke utbetaling_utbetalt:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val utbetaling = lagreUtbetaling(packet, utbetalingDao)
        utbetaling.avgjørVidereBehandling(vedtakFattetDao, vedtakMediator)
    }
}

