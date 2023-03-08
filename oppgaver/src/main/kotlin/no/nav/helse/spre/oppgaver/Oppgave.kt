package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

class Oppgave(
    val hendelseId: UUID,
    val dokumentId: UUID,
    var tilstand: Tilstand = Tilstand.DokumentOppdaget,
    val dokumentType: DokumentType,
    val sistEndret: LocalDateTime?,
) {
    private val erSøknad = dokumentType == DokumentType.Søknad
    private var observer: Observer? = null

    fun setObserver(observer: Observer) = apply {
        this.observer = observer
    }

    interface Observer {
        fun lagre(oppgave: Oppgave) {}
        fun publiser(oppgave: Oppgave) {}
        fun forlengTimeout(oppgave: Oppgave, timeout: LocalDateTime)
    }

    // Skal ikke sende forleng-signal for oppgaver som allerede er avluttet
    fun forlengTimeout() = if (kanUtsettes()) observer?.forlengTimeout(this, LocalDateTime.now().plusDays(180)) else Unit

    private fun kanUtsettes() = tilstand == Tilstand.SpleisLest || tilstand == Tilstand.KortInntektsmeldingFerdigbehandlet

    fun håndter(hendelse: Hendelse.TilInfotrygd) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.Avsluttet) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.Lest) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.AvsluttetUtenUtbetaling) = tilstand.håndter(this, hendelse)
    fun håndter(hendelse: Hendelse.VedtaksperiodeVenter) = tilstand.håndter(this, hendelse)

    private fun tilstand(tilstand: Tilstand) {
        val forrigeTilstand = this.tilstand
        this.tilstand = tilstand
        observer?.lagre(this)
        tilstand.entering(this, forrigeTilstand)
    }


    sealed class Tilstand {
        protected val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

        open fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
            oppgave.observer?.publiser(oppgave)
        }

        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.TilInfotrygd) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvbruttOgHarRelatertUtbetaling) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.Avsluttet) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.Lest) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvsluttetUtenUtbetaling) {}
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.VedtaksperiodeVenter) {}

        object SpleisFerdigbehandlet : Tilstand() { }

        object LagOppgave : Tilstand()

        object LagOppgaveForSpeilsaksbehandlere : Tilstand()

        object SpleisLest : Tilstand() {
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
                sikkerlogg.info("Ville utsatt oppgave i tilstand SpleisLest for ${oppgave.dokumentType.name}). {}, {}",
                    keyValue("hendelseId", oppgave.hendelseId),
                    keyValue("dokumentId", oppgave.dokumentId)
                )
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
                sikkerlogg.info("Ville utsatt oppgave i tilstand KortInntektsmeldingFerdigbehandlet. {}, {}",
                    keyValue("hendelseId", oppgave.hendelseId),
                    keyValue("dokumentId", oppgave.dokumentId)
                )
            }
        }

        object KortSøknadFerdigbehandlet: Tilstand() {
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

enum class DokumentType {
    Inntektsmelding, Søknad
}
