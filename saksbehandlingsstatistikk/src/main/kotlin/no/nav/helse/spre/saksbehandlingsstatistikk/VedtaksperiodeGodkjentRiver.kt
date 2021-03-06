package no.nav.helse.spre.saksbehandlingsstatistikk

import io.prometheus.client.Counter
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

private val counter = Counter.build("vedtaksperiode_godkjent_events", "Teller antall events av type vedtaksperiode_godkjent").register()

internal class VedtaksperiodeGodkjentRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_godkjent")
                message.requireKey("vedtaksperiodeId", "saksbehandlerIdent", "@opprettet", "automatiskBehandling")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeGodkjentData.fromJson(packet)
        val søknader = søknadDao.finnSøknader(vedtak.vedtaksperiodeId)
        if (søknader.isEmpty()) {
            log.info("Kunne ikke finne søknad for vedtaksperiode ${vedtak.vedtaksperiodeId}")
            return
        }
        søknader.forEach { søknadDao.upsertSøknad(vedtak.anrik(it)) }
        log.info("vedtaksperiode_godkjent lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
        counter.inc()
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("Forstod ikke vedtaksperiode_godkjent (se sikkerLog for detaljer)")
        tjenestekall.error("Forstod ikke vedtaksperiode_godkjent: ${problems.toExtendedReport()}")
    }

}
