package no.nav.helse.spre.saksbehandlingsstatistikk

import io.prometheus.client.Counter
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

private val counter = Counter.build("vedtak_fattet_events", "Teller antall events av type vedtak_fattet").register()

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val spreService: SpreService,
    private val søknadDao: SøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtak_fattet")
                message.requireKey("hendelser", "aktørId", "vedtaksperiodeId")
                message.interestedIn("@forårsaket_av.event_name", "@opprettet")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val vedtak = VedtakFattetData.fromJson(packet)
        val søknad = søknadDao.finnSøknad(vedtak.hendelser)
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
                "Noe gikk galt under behandling av vedtak_fattet \nmelding: {}\n søknad: {}\n error: {}",
                packet.toJson(),
                anriketSøknad,
                e
            )

            val søknadFraHendelser = søknadDao.finnSøknad(vedtak.hendelser)
            val søknadFraVedtaksperiodeID = søknadDao.finnSøknad(vedtak.vedtaksperiodeId)
            tjenestekall.info(
                "søknad fra hendelser: {}. søknad fra vedtaksperiodeId: {}",
                søknadFraHendelser,
                søknadFraVedtaksperiodeID
            )
            throw e
        }
        log.info("vedtak_fattet lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
        counter.inc()
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Noe gikk galt: {}", problems.toExtendedReport())
    }

}
