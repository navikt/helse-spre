package no.nav.helse.spre.oppgaver

import java.time.LocalDateTime
import java.util.*

class Oppgave(
    val hendelseId: UUID,
    val dokumentId: UUID,
    val fødselsnummer: String?,
    val orgnummer: String?,
    var tilstand: Tilstand = Tilstand.DokumentOppdaget,
    val dokumentType: DokumentType,
    val sistEndret: LocalDateTime?,
) {
    private val erSøknad = dokumentType == DokumentType.Søknad
    private var observer: Observer = object : Observer {}

    fun setObserver(observer: Observer) = apply {
        this.observer = observer
    }

    interface Observer {
        fun lagre(oppgave: Oppgave) {}

        fun lagOppgaveSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun lagOppgaveSpeilsaksbehandlereSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun lagOppgaveInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}
        fun lagOppgaveSpeilsaksbehandlereInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}

        fun ferdigbehandletSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun ferdigbehandletInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}
        fun lestSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun lestInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}
        fun kortSøknadFerdigbehandlet(hendelseId: UUID, dokumentId: UUID) {}
        fun kortInntektsmeldingFerdigbehandlet(hendelseId: UUID, dokumentId: UUID) {}
        fun venterPåGodkjenningSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun venterPåGodkjenningInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}
        fun avventerGodkjenningSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun avventerGodkjenningInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}
    }

    fun håndter(hendelse: Hendelse.TilInfotrygd) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.Avsluttet) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.Lest) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.AvsluttetUtenUtbetaling) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.VedtaksperiodeVenter) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.AvventerGodkjenning) = tilstand.håndter(this, hendelse)

    private fun tilstand(tilstand: Tilstand) {
        val forrigeTilstand = this.tilstand
        this.tilstand = tilstand
        observer.lagre(this)
        tilstand.entering(this, forrigeTilstand)
    }


    sealed class Tilstand {
        abstract fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand)

        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.TilInfotrygd) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.Avsluttet) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.Lest) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvsluttetUtenUtbetaling) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.VedtaksperiodeVenter) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvventerGodkjenning) {}

        object SpleisFerdigbehandlet : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                oppgave.dokumentType.ferdigbehandlet(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object LagOppgave : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                oppgave.dokumentType.lagOppgave(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object LagOppgaveForSpeilsaksbehandlere : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                oppgave.dokumentType.lagOppgaveSpeilsaksbehandlere(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object SpleisLest : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                oppgave.dokumentType.dokumentLest(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.TilInfotrygd) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) {
                oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.Avsluttet) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvsluttetUtenUtbetaling) {
                oppgave.tilstand(if (oppgave.erSøknad) KortSøknadFerdigbehandlet else KortInntektsmeldingFerdigbehandlet)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.VedtaksperiodeVenter) {
                oppgave.dokumentType.venterPåGodkjenning(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvventerGodkjenning) {
                // todo: kunne kanskje endret tilstand her istedenfor 🤔
                oppgave.dokumentType.avventerGodkjenning(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object DokumentOppdaget : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {}
            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.TilInfotrygd) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) {
                oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.Avsluttet) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.Lest) {
                oppgave.tilstand(SpleisLest)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvsluttetUtenUtbetaling) {
                oppgave.tilstand(if (oppgave.erSøknad) KortSøknadFerdigbehandlet else KortInntektsmeldingFerdigbehandlet)
            }
        }

        object KortInntektsmeldingFerdigbehandlet: Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                oppgave.observer.kortInntektsmeldingFerdigbehandlet(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.TilInfotrygd) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) {
                oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.Avsluttet) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.VedtaksperiodeVenter) {
                oppgave.observer.venterPåGodkjenningInntektsmelding(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvventerGodkjenning) {
                oppgave.observer.avventerGodkjenningInntektsmelding(oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object KortSøknadFerdigbehandlet: Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                oppgave.observer.kortSøknadFerdigbehandlet(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.TilInfotrygd) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.Avsluttet) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }
        }

        override fun toString(): String {
            return this.javaClass.simpleName
        }
    }
}

sealed interface DokumentType {
    fun lagOppgave(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID)
    fun ferdigbehandlet(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID)
    fun lagOppgaveSpeilsaksbehandlere(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID)
    fun dokumentLest(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID)
    fun venterPåGodkjenning(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID)
    fun avventerGodkjenning(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID)

    object Inntektsmelding : DokumentType {
        override fun ferdigbehandlet(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.ferdigbehandletInntektsmelding(hendelseId, dokumentId)
        }

        override fun lagOppgave(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.lagOppgaveInntektsmelding(hendelseId, dokumentId)
        }

        override fun lagOppgaveSpeilsaksbehandlere(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.lagOppgaveSpeilsaksbehandlereInntektsmelding(hendelseId, dokumentId)
        }

        override fun dokumentLest(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.lestInntektsmelding(hendelseId, dokumentId)
        }

        override fun venterPåGodkjenning(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.venterPåGodkjenningInntektsmelding(hendelseId, dokumentId)
        }

        override fun avventerGodkjenning(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.avventerGodkjenningInntektsmelding(hendelseId, dokumentId)
        }
    }

    object Søknad : DokumentType {
        override fun ferdigbehandlet(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.ferdigbehandletSøknad(hendelseId, dokumentId)
        }

        override fun lagOppgave(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.lagOppgaveSøknad(hendelseId, dokumentId)
        }

        override fun lagOppgaveSpeilsaksbehandlere(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.lagOppgaveSpeilsaksbehandlereSøknad(hendelseId, dokumentId)
        }

        override fun dokumentLest(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.lestSøknad(hendelseId, dokumentId)
        }

        override fun venterPåGodkjenning(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.venterPåGodkjenningSøknad(hendelseId, dokumentId)
        }

        override fun avventerGodkjenning(observer: Oppgave.Observer, hendelseId: UUID, dokumentId: UUID) {
            observer.avventerGodkjenningSøknad(hendelseId, dokumentId)
        }
    }
}
