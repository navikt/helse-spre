package no.nav.helse.spre.oppgaver

import no.nav.helse.spre.oppgaver.Oppgave.Tilstand

enum class Hendelse(val nesteTilstand: Tilstand) {
    TilInfotrygd(Tilstand.LagOppgave),
    Avsluttet(Tilstand.SpleisFerdigbehandlet),
    AvsluttetUtenUtbetaling(Tilstand.KortSøknadFerdigbehandlet),
    Lest(Tilstand.SpleisLest),
    MottattInntektsmeldingIAvsluttetUtenUtbetaling(Tilstand.KortInntektsmeldingFerdigbehandlet);
}
