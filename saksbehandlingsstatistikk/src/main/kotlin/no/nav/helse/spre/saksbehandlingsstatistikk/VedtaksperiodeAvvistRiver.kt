package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

internal class VedtaksperiodeAvvistRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_avvist")
                message.requireKey("vedtaksperiodeId", "saksbehandlerIdent", "@opprettet", "automatiskBehandling")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeAvvistData.fromJson(packet)
        val søknad = søknadDao.finnSøknad(vedtak.vedtaksperiodeId)

        if (søknad == null) {
            log.info("Kunne ikke finne søknad for vedtaksperiode ${vedtak.vedtaksperiodeId}")
            return
        }
        søknadDao.upsertSøknad(vedtak.anrik(søknad))
        log.info("vedtaksperiode_avvist lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Melding matchet ikke alle valideringene: {}", problems.toExtendedReport())
    }

}
