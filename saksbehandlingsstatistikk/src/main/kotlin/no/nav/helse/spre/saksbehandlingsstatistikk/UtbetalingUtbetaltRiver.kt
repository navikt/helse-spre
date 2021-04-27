package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class UtbetalingUtbetaltRiver(
    rapidsConnection: RapidsConnection,
    private val koblingDao: KoblingDao,
    private val søknadDao: SøknadDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "utbetaling_utbetalt")
                message.requireKey("utbetalingId", "ident")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val utbetalingUtbetalt = UtbetalingUtbetaltData(
            utbetalingId = packet["utbetalingId"].asUuid(),
            saksbehandlerIdent = packet["ident"].asText()
        )

        val søknadId = requireNotNull(koblingDao.finnSøknadIdForUtbetalingId(utbetalingUtbetalt.utbetalingId)) { "Fant ikke søknadId for utbetalingId" }
        søknadDao.settSaksbehandlerPåSøknad(utbetalingUtbetalt.saksbehandlerIdent, søknadId)
        log.info("utbetaling_utbetalt lest inn for utbetaling med id {}", packet["utbetalingId"].asText())
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }

    override fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {
        if (!error.problems.toExtendedReport().contains("Demanded @event_name is not string"))
            tjenestekall.info("Noe gikk galt: {}", error.problems.toExtendedReport())
    }

    private fun JsonNode.asUuid(): UUID = UUID.fromString(asText())
}
