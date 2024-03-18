package no.nav.helse.spre.styringsinfo.teamsak.behandling

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException
import java.time.LocalDateTime
import java.time.LocalDateTime.MIN
import java.util.UUID

internal data class SakId(val id: UUID) {
    override fun toString() = "$id"
}

internal fun UUID.asSakId() = SakId(this)

internal data class BehandlingId(val id: UUID) {
    override fun toString() = "$id"
}

internal data class Behandling(
    internal val sakId: SakId,                       // SakId er team sak-terminologi for vedtaksperiodeId
    internal val behandlingId: BehandlingId,
    internal val relatertBehandlingId: BehandlingId?,
    internal val aktørId: String,
    internal val mottattTid: LocalDateTime,          // Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.
    internal val registrertTid: LocalDateTime,       // Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
    internal val funksjonellTid: LocalDateTime,      // Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
    internal val behandlingstatus: Behandlingstatus,
    internal val behandlingstype: Behandlingstype,
    internal val periodetype: Periodetype? = null,
    internal val behandlingsresultat: Behandlingsresultat? = null,
    internal val behandlingskilde: Behandlingskilde,
    internal val behandlingsmetode: Behandlingsmetode?,
    internal val mottaker: Mottaker? = null,
    internal val saksbehandlerEnhet: String? = null,
    internal val beslutterEnhet: String? = null,
) {
    internal enum class Behandlingstatus {
        REGISTRERT,
        VURDERER_INNGANGSVILKÅR,
        AVVENTER_GODKJENNING,
        GODKJENT,
        AVSLUTTET
    }

    internal enum class Periodetype {
        FØRSTEGANGSBEHANDLING,
        FORLENGELSE
    }

    internal enum class Behandlingstype {
        SØKNAD,
        OMGJØRING,
        REVURDERING
    }
    
    internal enum class Behandlingsresultat {
        VEDTATT, // Vi har fått mer granulære resultater (innvilget/delvis innvilget/avslag), men trenger fortsatt denne for tidligere behandlinger
        INNVILGET,
        DELVIS_INNVILGET,
        AVSLAG,
        HENLAGT,
        AVBRUTT
    }

    internal enum class Behandlingskilde {
        SYKMELDT,
        ARBEIDSGIVER,
        SAKSBEHANDLER,
        SYSTEM
    }

    internal enum class Behandlingsmetode {
        MANUELL,
        AUTOMATISK
    }

    internal enum class Mottaker {
        SYKMELDT,
        ARBEIDSGIVER,
        SYKMELDT_OG_ARBEIDSGIVER,
        INGEN
    }

    private fun funksjoneltLik(other: Behandling): Boolean {
        return copy(funksjonellTid = MIN, behandlingsmetode = null) == other.copy(funksjonellTid = MIN, behandlingsmetode = null)
    }

    class Builder(private val forrige: Behandling) {

        private var behandlingstatus: Behandlingstatus? = null
        private var periodetype: Periodetype? = null
        private var behandlingsresultat: Behandlingsresultat? = null
        private var mottaker: Mottaker? = null
        private var saksbehandlerEnhet: String? = null
        private var beslutterEnhet: String? = null

        internal fun behandlingstatus(behandlingstatus: Behandlingstatus) = apply { this.behandlingstatus = behandlingstatus }
        internal fun periodetype(periodetype: Periodetype) = apply { this.periodetype = periodetype }
        internal fun behandlingsresultat(behandlingsresultat: Behandlingsresultat?) = apply { this.behandlingsresultat = behandlingsresultat }
        internal fun mottaker(mottaker: Mottaker?) = apply { this.mottaker = mottaker }
        internal fun saksbehandlerEnhet(saksbehandlerEnhet: String?) = apply { this.saksbehandlerEnhet = saksbehandlerEnhet }
        internal fun beslutterEnhet(beslutterEnhet: String?) = apply { this.beslutterEnhet = beslutterEnhet }

        internal fun build(funksjonellTid: LocalDateTime, behandlingsmetode: Behandlingsmetode): Behandling? {
            val ny = Behandling(
                funksjonellTid = funksjonellTid,
                behandlingsmetode = behandlingsmetode,
                sakId = forrige.sakId,
                behandlingId = forrige.behandlingId,
                relatertBehandlingId = forrige.relatertBehandlingId,
                aktørId = forrige.aktørId,
                mottattTid = forrige.mottattTid,
                registrertTid = forrige.registrertTid,
                behandlingstype = forrige.behandlingstype,
                behandlingskilde = forrige.behandlingskilde,
                behandlingstatus = behandlingstatus ?: forrige.behandlingstatus,
                periodetype = periodetype ?: forrige.periodetype,
                behandlingsresultat = behandlingsresultat ?: forrige.behandlingsresultat,
                saksbehandlerEnhet = saksbehandlerEnhet ?: forrige.saksbehandlerEnhet,
                beslutterEnhet = beslutterEnhet ?: forrige.beslutterEnhet,
                mottaker = mottaker ?: forrige.mottaker
            )

            if (ny.funksjoneltLik(forrige)) {
                sikkerLogg.info("Lagrer _ikke_ ny rad. Behandlingen er funksjonelt lik siste rad")
                return null
            }

            if (ny.behandlingsresultat == Behandlingsresultat.VEDTATT) {
                sikkerLogg.warn("Nå lagrer vi en rad i behandlingshendelse med behandlingsresultatt VEDTATT, det virker riv ruskende rart. Ta en titt på behandlingen")
            }

            if (forrige.behandlingstatus == Behandlingstatus.AVSLUTTET) {
                "Nå prøvde jeg å lagre en ny rad på samme behandling, selv om status er AVSLUTTET. Det må være en feil, ta en titt!".let { feilmelding ->
                    sikkerLogg.error(feilmelding)
                    throw IllegalStateException(feilmelding)
                }
            }

            return ny
        }

        private companion object {
            private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
        }
    }
}