package no.nav.helse.spre.oppgaver

import java.time.LocalDateTime
import java.util.*

class Oppgave(
    val hendelseId: UUID,
    val dokumentId: UUID,
    var tilstand: Tilstand = Tilstand.DokumentOppdaget,
    val dokumentType: DokumentType,
    val sistEndret: LocalDateTime?,
) {
    private var observer: Observer? = null
    fun setObserver(observer: Observer) = apply {
        this.observer = observer
    }

    interface Observer {
        fun lagre(oppgave: Oppgave) {}
        fun publiser(oppgave: Oppgave) {}
    }

    fun håndter(hendelse: Hendelse) =
        if (hendelse in tilstand.støttedeOverganger()) tilstand(hendelse.nesteTilstand)
        else Unit

    private fun tilstand(tilstand: Tilstand) {
        this.tilstand = tilstand
        observer?.lagre(this)
        observer?.publiser(this)
    }

    enum class Tilstand {
        SpleisFerdigbehandlet,
        LagOppgave,
        SpleisLest,
        DokumentOppdaget,
        KortInntektsmeldingFerdigbehandlet,
        KortSøknadFerdigbehandlet;

        /**
         * Hver tilstand oppgir hvilke hendelser som kan trigge tilstandsendring.
         */
        fun støttedeOverganger() = when (this) {
            DokumentOppdaget -> listOf(
                Hendelse.TilInfotrygd,
                Hendelse.Avsluttet,
                Hendelse.Lest,
                Hendelse.AvsluttetUtenUtbetaling,
                Hendelse.MottattInntektsmeldingIAvsluttetUtenUtbetaling
            )
            SpleisLest -> listOf(
                Hendelse.TilInfotrygd,
                Hendelse.Avsluttet,
                Hendelse.AvsluttetUtenUtbetaling,
                Hendelse.MottattInntektsmeldingIAvsluttetUtenUtbetaling
            )
            KortSøknadFerdigbehandlet -> listOf(Hendelse.Avsluttet)
            KortInntektsmeldingFerdigbehandlet -> listOf(Hendelse.TilInfotrygd, Hendelse.Avsluttet)
            SpleisFerdigbehandlet, LagOppgave -> emptyList()
        }

        override fun toString(): String {
            return this.javaClass.simpleName
        }
    }
}

enum class DokumentType {
    Inntektsmelding, Søknad
}
