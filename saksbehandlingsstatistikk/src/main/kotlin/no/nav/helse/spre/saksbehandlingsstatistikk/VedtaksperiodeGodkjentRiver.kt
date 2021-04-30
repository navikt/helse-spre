package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeGodkjentRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_godkjent")
                message.requireKey("vedtaksperiodeId", "saksbehandlerIdent")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeGodkjentData.fromJson(packet)
        val søknad = søknadDao.finnSøknad(vedtak.vedtaksperiodeId)

        if (søknad == null) {
            log.info("Kunne ikke finne søknad for vedtaksperiode ${vedtak.vedtaksperiodeId}")
            return
        }
        søknadDao.upsertSøknad(søknad.saksbehandlerIdent(vedtak.saksbehandlerIdent))
        log.info("vedtaksperiode_godkjent lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }

    override fun onSevere(error: MessageProblems.MessageException, context: MessageContext) {
        if (!error.problems.toExtendedReport().contains("Demanded @event_name is not string"))
            tjenestekall.info("Noe gikk galt: {}", error.problems.toExtendedReport())
    }
}