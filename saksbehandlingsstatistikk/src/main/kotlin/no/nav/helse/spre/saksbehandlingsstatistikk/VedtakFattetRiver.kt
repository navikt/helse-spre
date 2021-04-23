package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val spreService: SpreService
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtak_fattet")
                message.requireKey("hendelser", "@opprettet", "utbetalingId", "aktørId", "vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtakFattetData(
            aktørId = packet["aktørId"].asText(),
            hendelser = packet["hendelser"].map { it.asUuid() },
            utbetalingId = packet["utbetalingId"].asUuid(),
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid()
        )

        spreService.spre(vedtak)
        log.info("vedtaksperiode_fattet lest inn for vedtaksperiode med id {}", packet["vedtaksperiodeId"].asText())
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
