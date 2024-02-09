package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.time.LocalDateTime
import java.time.LocalDateTime.MIN
import java.util.UUID

internal data class SakId(val id: UUID) {
    override fun toString() = "$id"
}

internal data class BehandlingId(val id: UUID) {
    override fun toString() = "$id"
}

internal data class Behandling(
    internal val sakId: SakId,
    internal val behandlingId: BehandlingId,
    internal val relatertBehandlingId: BehandlingId?,
    internal val aktørId: String,
    internal val mottattTid: LocalDateTime,          // Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.
    internal val registrertTid: LocalDateTime,       // Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
    internal val funksjonellTid: LocalDateTime,      // Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
    internal val behandlingstatus: Behandlingstatus,
    internal val behandlingstype: Behandlingstype,
    internal val behandlingsresultat: Behandlingsresultat? = null,
    internal val behandlingskilde: Behandlingskilde,
    internal val behandlingsmetode: Behandlingsmetode?
) {
    internal enum class Behandlingstatus {
        Registrert,
        AvventerGodkjenning,
        Avsluttet
    }

    internal enum class Behandlingstype {
        Førstegangsbehandling,
        Omgjøring,
        Revurdering
    }
    
    internal enum class Behandlingsresultat {
        Vedtatt, // Per nå har vi ikke nok info til å utlede innvilget/delvisInnvilget/avslag, så alt sendes som ☂️-betegnelsen Vedtatt
        Henlagt,
        Avbrutt
    }

    internal enum class Behandlingskilde {
        Sykmeldt,
        Arbeidsgiver,
        Saksbehandler,
        System
    }

    internal enum class Behandlingsmetode {
        Manuell,
        Automatisk
    }

    internal fun funksjoneltLik(other: Behandling): Boolean {
        return copy(funksjonellTid = MIN) == other.copy(funksjonellTid = MIN)
    }

    class Builder(private val forrige: Behandling) {

        private var behandlingstatus: Behandlingstatus? = null
        private var behandlingtype: Behandlingstype? = null
        private var behandlingsresultat: Behandlingsresultat? = null
        private var behandlingskilde: Behandlingskilde? = null
        private var behandlingsmetode: Behandlingsmetode? = null

        internal fun behandlingstatus(behandlingstatus: Behandlingstatus) = apply { this.behandlingstatus = behandlingstatus }
        internal fun behandlingtype(behandlingtype: Behandlingstype) = apply { this.behandlingtype = behandlingtype }
        internal fun behandlingsresultat(behandlingsresultat: Behandlingsresultat) = apply { this.behandlingsresultat = behandlingsresultat }
        internal fun behandlingskilde(behandlingskilde: Behandlingskilde) = apply { this.behandlingskilde = behandlingskilde }
        internal fun behandlingsmetode(behandlingsmetode: Behandlingsmetode) = apply { this.behandlingsmetode = behandlingsmetode }

        internal fun build(funksjonellTid: LocalDateTime) = Behandling(
            sakId = forrige.sakId,
            behandlingId = forrige.behandlingId,
            relatertBehandlingId = forrige.relatertBehandlingId,
            aktørId = forrige.aktørId,
            mottattTid = forrige.mottattTid,
            registrertTid = forrige.registrertTid,
            funksjonellTid = funksjonellTid,
            behandlingsmetode = behandlingsmetode,
            behandlingstatus = behandlingstatus ?: forrige.behandlingstatus,
            behandlingstype = behandlingtype ?: forrige.behandlingstype,
            behandlingsresultat = behandlingsresultat ?: forrige.behandlingsresultat,
            behandlingskilde = behandlingskilde ?: forrige.behandlingskilde
        )
    }
}