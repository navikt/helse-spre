package no.nav.helse.spre.saksbehandlingsstatistikk

internal class SpreService(
    private val utgiver: Utgiver,
    private val søknadDao: SøknadDao
) {

    internal fun spre(vedtakFattetData: VedtakFattetData) {
        val søknad =
            requireNotNull(søknadDao.finnSøknad(vedtakFattetData.hendelser)) { "Finner ikke søknad for vedtak_fattet" }
        utgiver.publiserStatistikk(StatistikkEvent.toStatistikkEvent(søknad, vedtakFattetData))
    }
}

