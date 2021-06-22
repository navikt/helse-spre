package no.nav.helse.spre.saksbehandlingsstatistikk

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

internal class SpreService(
    private val utgiver: Utgiver,
    private val søknadDao: SøknadDao
) {
    internal fun spre(vedtakFattetData: VedtakFattetData) {
        søknadDao.finnSøknader(vedtakFattetData.hendelser)
            .takeIf { it.isNotEmpty() }
            ?.forEach { spre(it, vedtakFattetData.aktørId) }
            ?: throw IllegalStateException("Finner ikke søknad for vedtak_fattet, med hendelseIder=${vedtakFattetData.vedtaksperiodeId}")
    }

    internal fun spre(vedtaksperiodeForkastetData: VedtaksperiodeForkastetData) {
        søknadDao.finnSøknader(vedtaksperiodeForkastetData.vedtaksperiodeId)
            .takeIf { it.isNotEmpty() }
            ?.forEach { spre(it, vedtaksperiodeForkastetData.aktørId) }
            ?: throw IllegalStateException("Finner ikke søknad for vedtaksperiode_forkastet, med vedtaksperiodeId=${vedtaksperiodeForkastetData.vedtaksperiodeId}")
    }

    private fun spre(søknad: Søknad, aktørId: String) {
        val melding = StatistikkEvent.statistikkEvent(søknad, aktørId)
        utgiver.publiserStatistikk(melding)
    }
}
