package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments.keyValue
import org.slf4j.LoggerFactory
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
    private var observer: Observer? = null

    fun setObserver(observer: Observer) = apply {
        this.observer = observer
    }

    interface Observer {
        fun lagre(oppgave: Oppgave) {}

        fun lagOppgaveSøknad(hendelseId: UUID, dokumentId: UUID) {}
        fun lagOppgaveInntektsmelding(hendelseId: UUID, dokumentId: UUID) {}

        fun publiser(oppgave: Oppgave) {}
        fun forlengTimeout(oppgave: Oppgave, timeout: LocalDateTime)
        fun forlengTimeoutUtenUtbetalingTilSøker(oppgave: Oppgave, timeout: LocalDateTime): Boolean
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
        open fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvventerGodkjenning) {}

        object SpleisFerdigbehandlet : Tilstand() { }

        object LagOppgave : Tilstand() {
            override fun entering(oppgave: Oppgave, forrigeTilstand: Tilstand) {
                when (oppgave.dokumentType) {
                    DokumentType.Søknad -> oppgave.observer?.lagOppgaveSøknad(oppgave.hendelseId, oppgave.dokumentId)
                    DokumentType.Inntektsmelding -> oppgave.observer?.lagOppgaveInntektsmelding(oppgave.hendelseId, oppgave.dokumentId)
                }
            }
        }

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
                if (oppgave.observer?.forlengTimeoutUtenUtbetalingTilSøker(oppgave, LocalDateTime.now().plusDays(10)) == true) return
                sikkerlogg.info("Vi utsetter ikke oppgave i tilstand SpleisLest ettersom det er utbetaling til søker. {}, {}",
                    keyValue("hendelseId", oppgave.hendelseId),
                    keyValue("dokumentId", oppgave.dokumentId)
                )
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvventerGodkjenning) {
                oppgave.observer?.forlengTimeout(oppgave, LocalDateTime.now().plusDays(180))
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
                if (oppgave.observer?.forlengTimeoutUtenUtbetalingTilSøker(oppgave, LocalDateTime.now().plusDays(10)) == true) return
                sikkerlogg.info("Vi utsetter ikke oppgave i tilstand KortInntektsmeldingFerdigbehandlet ettersom det er utbetaling til søker. {}, {}",
                    keyValue("hendelseId", oppgave.hendelseId),
                    keyValue("dokumentId", oppgave.dokumentId)
                )
            }

            override fun håndter(oppgave: Oppgave, hendelse: Hendelse.AvventerGodkjenning) {
                oppgave.observer?.forlengTimeout(oppgave, LocalDateTime.now().plusDays(180))
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
