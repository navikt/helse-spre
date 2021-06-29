package no.nav.helse.spre.saksbehandlingsstatistikk

import io.prometheus.client.Counter
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

private val counter = Counter.build("vedtaksperiode_endret_events", "Teller antall events av type vedtaksperiode_endret").register()

internal class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_endret")
                message.requireKey("hendelser", "vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeEndretData.fromJson(packet)
        val søknader = søknadDao.finnSøknader(vedtak.hendelser)

        val søknaderUtenVedtaksperiodeId = søknader.filter { it.vedtaksperiodeId == null }
        if (søknaderUtenVedtaksperiodeId.isEmpty()) return

        søknaderUtenVedtaksperiodeId.forEach { søknadDao.upsertSøknad(it.copy(vedtaksperiodeId = vedtak.vedtaksperiodeId)) }

        log.info("vedtaksperiode_endret lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
        counter.inc()
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Melding matchet ikke alle valideringene: {}", problems.toExtendedReport())
    }

}
