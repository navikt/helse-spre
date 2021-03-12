package no.nav.helse.spre.saksbehandlingsstatistikk

import java.util.*
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val spreService: SpreService
) : River.PacketListener {

    private val tilstanderFørSøknadMottatt = listOf(
        "MOTTATT_SYKMELDING_FERDIG_FORLENGELSE",
        "MOTTATT_SYKMELDING_UFERDIG_FORLENGELSE",
        "MOTTATT_SYKMELDING_FERDIG_GAP",
        "MOTTATT_SYKMELDING_UFERDIG_GAP",
        "AVVENTER_SØKNAD_UFERDIG_FORLENGELSE",
        "AVVENTER_SØKNAD_UFERDIG_GAP",
        "AVVENTER_SØKNAD_FERDIG_GAP"
    )

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_endret")
                message.requireKey("hendelser")
                message.require("gjeldendeTilstand") { check(!tilstanderFørSøknadMottatt.contains(it.asText())) }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeEndretData(
            hendelser = packet["hendelser"].map { UUID.fromString(it.asText()) },
        )

        spreService.spre(vedtak)
        log.info("Vedtaksperiode endret lest inn")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }
}
