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
            requireNotNull(søknadDao.finnSøknad(vedtakFattetData.hendelser)) {
                "Finner ikke søknad for vedtak_fattet, med hendelseIder=${vedtakFattetData.hendelser}"
            }
        if (søknad.vedtaksperiodeId != vedtakFattetData.vedtaksperiodeId)
            log.info(
                "Hmm 🤨, lagret søknad og matchende vedtak_fattet har ikke samme vedtaksperiodeId. " +
                        "Søknadsdata har vedtaksperiodeId ${søknad.vedtaksperiodeId}, " +
                        "vedtak_fattet-event har vedtaksperiodeId ${vedtakFattetData.vedtaksperiodeId}"
            )
        utgiver.publiserStatistikk(StatistikkEvent.toStatistikkEvent(søknad, vedtakFattetData))
    }
}
