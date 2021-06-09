package no.nav.helse.spre.saksbehandlingsstatistikk

import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")
private val tjenestekall: Logger = LoggerFactory.getLogger("tjenestekall")

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

        if (søknad.bleAvsluttetAvSpleis) søknadDao.upsertSøknad(søknad.vedtakFattet(vedtak.vedtaksperiodeForkastet).automatiskBehandling(true).saksbehandlerIdent("SPLEIS")) else
            søknadDao.upsertSøknad(søknad.vedtakFattet(vedtak.vedtaksperiodeForkastet))


        try {
            spreService.spre(vedtak)
        } catch (e: Exception) {
            tjenestekall.info("Noe gikk galt under behandling av vedtaksperiode_forkastet: {}", packet.toJson())
            throw e
        }
        log.info("vedtaksperiode_forkastet lest inn for vedtaksperiode med id ${vedtak.vedtaksperiodeId}")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        tjenestekall.info("Melding matchet ikke alle valideringene: {}", problems.toExtendedReport())
    }

}
