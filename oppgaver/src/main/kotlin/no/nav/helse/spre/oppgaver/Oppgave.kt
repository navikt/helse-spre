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

    private fun tilstand(tilstand: Tilstand) {
        val forrigeTilstand = this.tilstand
        this.tilstand = tilstand
        observer?.lagre(this)
        tilstand.entering(this, forrigeTilstand)
    }

    sealed class Tilstand {
        open fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
            oppgave.observer?.publiser(oppgave)
        }

        open fun håndter(oppgave: Oppgave, hendelse: Hendelse) {}

        object SpleisFerdigbehandlet : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                if (forrigeTilstand == KortSøknadFerdigbehandlet) return
                super.entering(oppgave, forrigeTilstand)
            }
        }

        object LagOppgave : Tilstand()

        object SpleisLest : Tilstand() {
            override fun håndter(oppgave: Oppgave, hendelse: Hendelse) {
                when (hendelse) {
                    Hendelse.TilInfotrygd -> LagOppgave
                    Hendelse.Avsluttet -> SpleisFerdigbehandlet
                    Hendelse.AvsluttetUtenUtbetaling -> KortSøknadFerdigbehandlet
                    Hendelse.MottattInntektsmeldingIAvsluttetUtenUtbetaling -> KortInntektsmeldingFerdigbehandlet
                    else -> null
                }?.let(oppgave::tilstand)
            }
        }

        object DokumentOppdaget : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {}
            override fun håndter(oppgave: Oppgave, hendelse: Hendelse) {
                when (hendelse) {
                    Hendelse.TilInfotrygd -> LagOppgave
                    Hendelse.Avsluttet -> SpleisFerdigbehandlet
                    Hendelse.Lest -> SpleisLest
                    Hendelse.AvsluttetUtenUtbetaling -> KortSøknadFerdigbehandlet
                    Hendelse.MottattInntektsmeldingIAvsluttetUtenUtbetaling -> KortInntektsmeldingFerdigbehandlet
                }.let(oppgave::tilstand)
            }
        }

        object KortInntektsmeldingFerdigbehandlet: Tilstand() {
            override fun håndter(oppgave: Oppgave, hendelse: Hendelse) {
                when (hendelse) {
                    Hendelse.TilInfotrygd -> LagOppgave
                    Hendelse.Avsluttet -> SpleisFerdigbehandlet
                    else -> null
                }?.let { oppgave.tilstand(it) }
            }
        }

        object KortSøknadFerdigbehandlet: Tilstand() {
            override fun håndter(oppgave: Oppgave, hendelse: Hendelse) {
                when (hendelse) {
                    Hendelse.Avsluttet -> SpleisFerdigbehandlet
                    else -> null
                }?.let { oppgave.tilstand(it) }
            }
        }

        override fun toString(): String {
            return this.javaClass.simpleName
        }
    }
}

enum class DokumentType {
    Inntektsmelding, Søknad
}
