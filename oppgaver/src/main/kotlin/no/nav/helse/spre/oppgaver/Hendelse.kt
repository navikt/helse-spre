package no.nav.helse.spre.oppgaver

sealed class Hendelse {
    object TilInfotrygd : Hendelse()

    /**
     * Behandling ikke mulig i ny løsning.
     * Det er gjort utbetaling i spleis som må tas hensyn til ved behandling av oppgaven
     */
    object AvbruttOgHarRelatertUtbetaling : Hendelse()
    object Avsluttet : Hendelse()
    object AvsluttetUtenUtbetaling : Hendelse()
    object Lest : Hendelse()
    object VedtaksperiodeVenter: Hendelse()
    object AvventerGodkjenning: Hendelse()
}
