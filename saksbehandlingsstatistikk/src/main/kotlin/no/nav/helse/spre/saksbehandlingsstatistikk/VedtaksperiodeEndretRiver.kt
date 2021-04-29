package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_endret")
                message.requireValue("gjeldendeTilstand", "AVVENTER_GODKJENNING")
                message.requireKey("hendelser", "vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeEndretData(
            hendelser = packet["hendelser"].map { UUID.fromString(it.asText()) },
            vedtaksperiodeId = packet["vedtaksperiodeId"].asUuid(),
        )
        val søknad = søknadDao.finnSøknad(vedtak.hendelser)

        if (søknad == null) {
            log.info("Kunne ikke finne søknad for hendelser ${vedtak.hendelser}")
            return
        }
        søknadDao.upsertSøknad(søknad.vedtaksperiodeId(vedtak.vedtaksperiodeId))
        log.info("vedtaksperiode_endret lest inn for vedtaksperiode med id {}", packet["vedtaksperiodeId"].asText())
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
