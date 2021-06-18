package no.nav.helse.spre.saksbehandlingsstatistikk

import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

internal class SpreService(
    private val utgiver: Utgiver,
    private val søknadDao: SøknadDao
) {
    internal fun spre(vedtakFattetData: VedtakFattetData) {
        val søknad =
            checkNotNull(søknadDao.finnSøknad(vedtakFattetData.hendelser)) {
                "Finner ikke søknad for vedtak_fattet, med hendelseIder=${vedtakFattetData.vedtaksperiodeId}"
            }
        if (søknad.vedtaksperiodeId != vedtakFattetData.vedtaksperiodeId)
            log.info(
                "Hmm 🤨, lagret søknad og matchende vedtak_fattet har ikke samme vedtaksperiodeId. " +
                        "Søknadsdata har vedtaksperiodeId ${søknad.vedtaksperiodeId}, " +
                        "vedtak_fattet-event har vedtaksperiodeId ${vedtakFattetData.vedtaksperiodeId}"
            )

        spre(søknad, vedtakFattetData.aktørId)
    }

    internal fun spre(vedtaksperiodeForkastetData: VedtaksperiodeForkastetData) {
        val søknad =
            checkNotNull(søknadDao.finnSøknad(vedtaksperiodeForkastetData.vedtaksperiodeId)) {
                "Finner ikke søknad for vedtaksperiode_forkastet, med vedtaksperiodeId=${vedtaksperiodeForkastetData.vedtaksperiodeId}"
            }

        spre(søknad, vedtaksperiodeForkastetData.aktørId)
    }

    private fun spre(søknad: Søknad, aktørId: String) {
        val melding = StatistikkEvent.statistikkEvent(søknad, aktørId)
        utgiver.publiserStatistikk(melding)
    }
}
