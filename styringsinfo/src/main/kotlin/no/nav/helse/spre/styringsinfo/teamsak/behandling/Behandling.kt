package no.nav.helse.spre.styringsinfo.teamsak.behandling

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
    internal val behandlingsmetode: Behandlingsmetode?, // TODO: Hadde vært kjekt å migrere alle rader med siste=true til å ha en verdi, da hadde vi unngått at denne er nullable her i koden
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
        VEDTATT, // Per nå har vi ikke nok info til å utlede innvilget/delvisInnvilget/avslag, så alt sendes som ☂️-betegnelsen Vedtatt
        HENLAGT,
        AVBRUTT,
        VEDTAK_IVERKSATT
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

    internal fun funksjoneltLik(other: Behandling): Boolean {
        return copy(funksjonellTid = MIN) == other.copy(funksjonellTid = MIN)
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
        internal fun behandlingsresultat(behandlingsresultat: Behandlingsresultat) = apply { this.behandlingsresultat = behandlingsresultat }
        internal fun mottaker(mottaker: Mottaker?) = apply { this.mottaker = mottaker }
        internal fun saksbehandlerEnhet(saksbehandlerEnhet: String?) = apply { this.saksbehandlerEnhet = saksbehandlerEnhet }
        internal fun beslutterEnhet(beslutterEnhet: String?) = apply { this.beslutterEnhet = beslutterEnhet }

        internal fun build(funksjonellTid: LocalDateTime, behandlingsmetode: Behandlingsmetode) = Behandling(
            sakId = forrige.sakId,
            behandlingId = forrige.behandlingId,
            relatertBehandlingId = forrige.relatertBehandlingId,
            aktørId = forrige.aktørId,
            mottattTid = forrige.mottattTid,
            registrertTid = forrige.registrertTid,
            funksjonellTid = funksjonellTid,
            behandlingsmetode = behandlingsmetode,
            behandlingstatus = behandlingstatus ?: forrige.behandlingstatus,
            behandlingstype = forrige.behandlingstype,
            periodetype = periodetype ?: forrige.periodetype,
            behandlingsresultat = behandlingsresultat ?: forrige.behandlingsresultat,
            behandlingskilde = forrige.behandlingskilde,
            saksbehandlerEnhet = saksbehandlerEnhet ?: forrige.saksbehandlerEnhet,
            beslutterEnhet = beslutterEnhet ?: forrige.beslutterEnhet,
            mottaker = mottaker ?: forrige.mottaker
        )
    }
}