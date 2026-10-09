package no.nav.helse.spre.oppgaver

import java.time.LocalDateTime
import java.util.*

class Oppgave(
    val hendelseId: UUID,
    val dokumentId: UUID,
    val fødselsnummer: String?,
    val orgnummer: String?,
    var tilstand: Tilstand,
    val dokumentType: DokumentType,
    val sistEndret: LocalDateTime?,
    private val observer: Observer,
) {
    companion object {
        fun nyInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
            fødselsnummer: String,
            orgnummer: String,
            observer: Observer,
        ) = nyOppgve(hendelseId, dokumentId, fødselsnummer, orgnummer, DokumentType.Inntektsmelding, observer)

        fun nySøknad(
            hendelseId: UUID,
            dokumentId: UUID,
            fødselsnummer: String,
            orgnummer: String?,
            observer: Observer,
        ) = nyOppgve(hendelseId, dokumentId, fødselsnummer, orgnummer, DokumentType.Søknad, observer)

        private fun nyOppgve(
            hendelseId: UUID,
            dokumentId: UUID,
            fødselsnummer: String,
            orgnummer: String?,
            dokumentType: DokumentType,
            observer: Observer,
        ) = Oppgave(
            hendelseId = hendelseId,
            dokumentId = dokumentId,
            fødselsnummer = fødselsnummer,
            orgnummer = orgnummer,
            dokumentType = dokumentType,
            tilstand = Tilstand.Ny,
            sistEndret = LocalDateTime.now(),
            observer = observer,
        ).apply {
            dokumentOppdaget()
        }
    }

    private val erSøknad = dokumentType == DokumentType.Søknad

    interface Observer {
        fun oppgaveEndretTilstand(
            hendelseId: UUID,
            dokumentId: UUID,
            forrigeTilstand: Tilstand,
            nyTilstand: Tilstand,
        ) {}

        fun søknadOppdaget(
            fødselsnummer: String,
            orgnummer: String?,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun inntektsmeldingOppdaget(
            fødselsnummer: String,
            orgnummer: String?,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lagOppgaveSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lagOppgaveSpeilsaksbehandlereSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lagOppgaveInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lagOppgaveSpeilsaksbehandlereInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun ferdigbehandletSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun ferdigbehandletInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lestSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lestInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun lestInntektsmeldingFørSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun kortSøknadFerdigbehandlet(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun kortInntektsmeldingFerdigbehandlet(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun venterPåGodkjenningSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun venterPåGodkjenningInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun avventerGodkjenningSøknad(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}

        fun avventerGodkjenningInntektsmelding(
            hendelseId: UUID,
            dokumentId: UUID,
        ) {}
    }

    private fun dokumentOppdaget() = tilstand.dokumentOppdaget(this)

    fun lagOppgave() = tilstand.håndterLagOppgave(this)

    fun lagOppgavePåSpeilKø() = tilstand.håndterLagOppgavePåSpeilKø(this)

    fun håndter(hendelse: Hendelse.Avsluttet) = tilstand.håndter(this, hendelse)

    internal fun håndterLest() {
        tilstand.håndterLest(this)
    }

    internal fun håndterInntektsmeldingFørSøknad() {
        tilstand.håndterInntektsmeldingFørSøknad(this)
    }

    fun håndterInntektsmeldingIkkeHåndtert(speilRelatert: Boolean) {
        tilstand.håndterInntektsmeldingIkkeHåndtert(this, speilRelatert)
    }

    fun håndter(hendelse: Hendelse.AvsluttetUtenUtbetaling) = tilstand.håndter(this, hendelse)

    fun håndter(hendelse: Hendelse.VedtaksperiodeVenter) = tilstand.håndter(this, hendelse)

    fun håndter(hendelse: Hendelse.AvventerGodkjenning) = tilstand.håndter(this, hendelse)

    private fun tilstand(tilstand: Tilstand) {
        val forrigeTilstand = this.tilstand
        this.tilstand = tilstand
        observer.oppgaveEndretTilstand(hendelseId, dokumentId, forrigeTilstand, tilstand)
        tilstand.entering(this, forrigeTilstand)
    }

    sealed class Tilstand {
        abstract fun entering(
            oppgave: Oppgave,
            forrigeTilstand: Tilstand,
        )

        open fun dokumentOppdaget(oppgave: Oppgave): Unit = throw IllegalStateException("forventet ikke dokumentoppdaget!")

        open fun håndterLagOppgave(oppgave: Oppgave) {}

        open fun håndterLagOppgavePåSpeilKø(oppgave: Oppgave) {}

        open fun håndter(
            oppgave: Oppgave,
            hendelse: Hendelse.Avsluttet,
        ) {}

        open fun håndterLest(oppgave: Oppgave) {}

        open fun håndterInntektsmeldingFørSøknad(oppgave: Oppgave) {}

        open fun håndterInntektsmeldingIkkeHåndtert(
            oppgave: Oppgave,
            speilRelatert: Boolean,
        ) {}

        open fun håndter(
            oppgave: Oppgave,
            hendelse: Hendelse.AvsluttetUtenUtbetaling,
        ) {}

        open fun håndter(
            oppgave: Oppgave,
            hendelse: Hendelse.VedtaksperiodeVenter,
        ) {}

        open fun håndter(
            oppgave: Oppgave,
            hendelse: Hendelse.AvventerGodkjenning,
        ) {}

        object Ny : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {}

            override fun dokumentOppdaget(oppgave: Oppgave) {
                oppgave.tilstand(DokumentOppdaget)
            }
        }

        object DokumentOppdaget : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.dokumentType.dokumentOppdaget(oppgave.observer, oppgave.fødselsnummer!!, oppgave.orgnummer, oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndterLagOppgave(oppgave: Oppgave) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndterLagOppgavePåSpeilKø(oppgave: Oppgave) {
                oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.Avsluttet,
            ) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }

            override fun håndterLest(oppgave: Oppgave) {
                oppgave.tilstand(SpleisLest)
            }

            override fun håndterInntektsmeldingIkkeHåndtert(
                oppgave: Oppgave,
                speilRelatert: Boolean,
            ) {
                if (speilRelatert) {
                    oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
                } else {
                    oppgave.tilstand(LagOppgave)
                }
            }

            override fun håndterInntektsmeldingFørSøknad(oppgave: Oppgave) {
                oppgave.observer.lestInntektsmeldingFørSøknad(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.AvsluttetUtenUtbetaling,
            ) {
                oppgave.tilstand(if (oppgave.erSøknad) KortSøknadFerdigbehandlet else KortInntektsmeldingFerdigbehandlet)
            }
        }

        object SpleisFerdigbehandlet : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.dokumentType.ferdigbehandlet(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object LagOppgave : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.dokumentType.lagOppgave(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object LagOppgaveForSpeilsaksbehandlere : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.dokumentType.lagOppgaveSpeilsaksbehandlere(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object SpleisLest : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.dokumentType.dokumentLest(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndterLagOppgave(oppgave: Oppgave) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndterLagOppgavePåSpeilKø(oppgave: Oppgave) {
                oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.Avsluttet,
            ) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.AvsluttetUtenUtbetaling,
            ) {
                oppgave.tilstand(if (oppgave.erSøknad) KortSøknadFerdigbehandlet else KortInntektsmeldingFerdigbehandlet)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.VedtaksperiodeVenter,
            ) {
                oppgave.dokumentType.venterPåGodkjenning(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.AvventerGodkjenning,
            ) {
                // todo: kunne kanskje endret tilstand her istedenfor 🤔
                oppgave.dokumentType.avventerGodkjenning(oppgave.observer, oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object KortInntektsmeldingFerdigbehandlet : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.observer.ferdigbehandletInntektsmelding(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndterLagOppgave(oppgave: Oppgave) {
                oppgave.tilstand(LagOppgave)
            }

            override fun håndterLagOppgavePåSpeilKø(oppgave: Oppgave) {
                oppgave.tilstand(LagOppgaveForSpeilsaksbehandlere)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.Avsluttet,
            ) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.VedtaksperiodeVenter,
            ) {
                oppgave.observer.venterPåGodkjenningInntektsmelding(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.AvventerGodkjenning,
            ) {
                oppgave.observer.avventerGodkjenningInntektsmelding(oppgave.hendelseId, oppgave.dokumentId)
            }
        }

        object KortSøknadFerdigbehandlet : Tilstand() {
            override fun entering(
                oppgave: Oppgave,
                forrigeTilstand: Tilstand,
            ) {
                oppgave.observer.kortSøknadFerdigbehandlet(oppgave.hendelseId, oppgave.dokumentId)
            }

            override fun håndter(
                oppgave: Oppgave,
                hendelse: Hendelse.Avsluttet,
            ) {
                oppgave.tilstand(SpleisFerdigbehandlet)
            }
        }

        override fun toString(): String = this.javaClass.simpleName
    }
}

sealed interface DokumentType {
    fun dokumentOppdaget(
        observer: Oppgave.Observer,
        fødselsnummer: String,
        orgnummer: String?,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    fun lagOppgave(
        observer: Oppgave.Observer,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    fun ferdigbehandlet(
        observer: Oppgave.Observer,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    fun lagOppgaveSpeilsaksbehandlere(
        observer: Oppgave.Observer,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    fun dokumentLest(
        observer: Oppgave.Observer,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    fun venterPåGodkjenning(
        observer: Oppgave.Observer,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    fun avventerGodkjenning(
        observer: Oppgave.Observer,
        hendelseId: UUID,
        dokumentId: UUID,
    )

    object Inntektsmelding : DokumentType {
        override fun dokumentOppdaget(
            observer: Oppgave.Observer,
            fødselsnummer: String,
            orgnummer: String?,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.inntektsmeldingOppdaget(fødselsnummer, orgnummer, hendelseId, dokumentId)
        }

        override fun ferdigbehandlet(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.ferdigbehandletInntektsmelding(hendelseId, dokumentId)
        }

        override fun lagOppgave(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.lagOppgaveInntektsmelding(hendelseId, dokumentId)
        }

        override fun lagOppgaveSpeilsaksbehandlere(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.lagOppgaveSpeilsaksbehandlereInntektsmelding(hendelseId, dokumentId)
        }

        override fun dokumentLest(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.lestInntektsmelding(hendelseId, dokumentId)
        }

        override fun venterPåGodkjenning(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.venterPåGodkjenningInntektsmelding(hendelseId, dokumentId)
        }

        override fun avventerGodkjenning(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.avventerGodkjenningInntektsmelding(hendelseId, dokumentId)
        }
    }

    object Søknad : DokumentType {
        override fun dokumentOppdaget(
            observer: Oppgave.Observer,
            fødselsnummer: String,
            orgnummer: String?,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.søknadOppdaget(fødselsnummer, orgnummer, hendelseId, dokumentId)
        }

        override fun ferdigbehandlet(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.ferdigbehandletSøknad(hendelseId, dokumentId)
        }

        override fun lagOppgave(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.lagOppgaveSøknad(hendelseId, dokumentId)
        }

        override fun lagOppgaveSpeilsaksbehandlere(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.lagOppgaveSpeilsaksbehandlereSøknad(hendelseId, dokumentId)
        }

        override fun dokumentLest(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.lestSøknad(hendelseId, dokumentId)
        }

        override fun venterPåGodkjenning(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.venterPåGodkjenningSøknad(hendelseId, dokumentId)
        }

        override fun avventerGodkjenning(
            observer: Oppgave.Observer,
            hendelseId: UUID,
            dokumentId: UUID,
        ) {
            observer.avventerGodkjenningSøknad(hendelseId, dokumentId)
        }
    }
}
