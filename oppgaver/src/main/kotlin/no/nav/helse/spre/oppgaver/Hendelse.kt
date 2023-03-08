package no.nav.helse.spre.oppgaver

sealed class Hendelse {
    abstract fun accept(oppgave: Oppgave)

    object TilInfotrygd : Hendelse() {
        override fun accept(oppgave: Oppgave) {
            oppgave.håndter(this)
        }
    }

    /**
     * Behandling ikke mulig i ny løsning.
     * Det er gjort utbetaling i spleis som må tas hensyn til ved behandling av oppgaven
     */
    object AvbruttOgHarRelatertUtbetaling : Hendelse() {
        override fun accept(oppgave: Oppgave) {
            oppgave.håndter(this)
        }
    }

    object Avsluttet : Hendelse() {
        override fun accept(oppgave: Oppgave) {
            oppgave.håndter(this)
        }
    }

    object AvsluttetUtenUtbetaling : Hendelse() {
        override fun accept(oppgave: Oppgave) {
            oppgave.håndter(this)
        }
    }

    object Lest : Hendelse() {
        override fun accept(oppgave: Oppgave) {
            oppgave.håndter(this)
        }
    }

    object VedtaksperiodeVenter: Hendelse() {
        override fun accept(oppgave: Oppgave) {
            oppgave.håndter(this)
        }
    }
}
