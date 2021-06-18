package no.nav.helse.spre.saksbehandlingsstatistikk

import io.prometheus.client.Counter
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

private val counter = Counter.build("vedtaksperiode_forkastet_events", "Teller antall events av type vedtaksperiode_forkastet").register()

internal class VedtaksperiodeForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val spreService: SpreService,
    private val søknadDao: SøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_forkastet")
                message.requireKey("@opprettet", "aktørId", "vedtaksperiodeId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtaksperiodeForkastetData.fromJson(packet)
        val søknad = søknadDao.finnSøknad(vedtak.vedtaksperiodeId)
        if (søknad == null) {
            log.info("Kunne ikke finne søknad for vedtaksperiode ${vedtak.vedtaksperiodeId}")
            return
        }

        val anriketSøknad = vedtak.anrik(søknad)
        søknadDao.upsertSøknad(anriketSøknad)

        try {
            spreService.spre(vedtak)
        } catch (e: Exception) {
            tjenestekall.info(
                "Noe gikk galt under behandling av vedtaksperiode_forkastet\n" +
                        "melding: {}\n" +
                        "søknad: {}\n" +
                        "error: {}",
                packet.toJson(),
                anriketSøknad,
                e
            )

            throw e
        }
        log.info("vedtaksperiode_forkastet lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
        counter.inc()
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Melding matchet ikke alle valideringene: {}", problems.toExtendedReport())
    }

}
