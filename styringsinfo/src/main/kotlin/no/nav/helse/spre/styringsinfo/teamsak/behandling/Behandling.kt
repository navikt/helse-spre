package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.time.OffsetDateTime
import java.time.OffsetDateTime.MIN
import java.util.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.enhet.AutomatiskEnhet
import no.nav.helse.spre.styringsinfo.teamsak.enhet.Enhet
import no.nav.helse.spre.styringsinfo.teamsak.enhet.FunnetEnhet
import no.nav.helse.spre.styringsinfo.teamsak.enhet.ManglendeEnhet
import org.slf4j.Logger
import org.slf4j.LoggerFactory

internal data class SakId(val id: UUID) {
    override fun toString() = "$id"
}

internal data class BehandlingId(val id: UUID) {
    override fun toString() = "$id"
}

internal data class Behandling(
    internal val yrkesaktivitetstype: String,
    internal val sakId: SakId,                       // SakId er team sak-terminologi for vedtaksperiodeId
    internal val behandlingId: BehandlingId,
    internal val relatertBehandlingId: BehandlingId?,
    internal val aktørId: String,
    internal val mottattTid: OffsetDateTime,          // Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.
    internal val registrertTid: OffsetDateTime,       // Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
    internal val funksjonellTid: OffsetDateTime,      // Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
    internal val behandlingstatus: Behandlingstatus,
    internal val behandlingstype: Behandlingstype,
    internal val periodetype: Periodetype? = null,
    internal val behandlingsresultat: Behandlingsresultat? = null,
    internal val behandlingskilde: Behandlingskilde,
    internal val behandlingsmetode: Metode,
    internal val hendelsesmetode: Metode,             // __INTERN__ verdi som ikke teles med Team 🎷
    internal val mottaker: Mottaker? = null,
    internal val saksbehandlerEnhet: String? = null,
    internal val beslutterEnhet: String? = null,
) {
    internal enum class Behandlingstatus {
        REGISTRERT,
        KOMPLETT_FAKTAGRUNNLAG,
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
        GJENÅPNING,
        REVURDERING
    }

    internal enum class Behandlingsresultat {
        INNVILGET,
        DELVIS_INNVILGET,
        AVSLAG,
        IKKE_REALITETSBEHANDLET,
        AVBRUTT,
        ANNULLERT
    }

    internal enum class Behandlingskilde {
        SYKMELDT,
        ARBEIDSGIVER,
        SAKSBEHANDLER,
        SYSTEM
    }

    internal enum class Metode {
        AUTOMATISK,
        MANUELL,
        TOTRINNS
    }

    internal enum class Mottaker {
        SYKMELDT,
        ARBEIDSGIVER,
        SYKMELDT_OG_ARBEIDSGIVER,
        INGEN
    }

    private fun funksjoneltLik(other: Behandling): Boolean {
        return copy(funksjonellTid = MIN, hendelsesmetode = Metode.AUTOMATISK) == other.copy(funksjonellTid = MIN, hendelsesmetode = Metode.AUTOMATISK)
    }

    class Builder(private val forrige: Behandling) {

        private var behandlingstatus: Behandlingstatus? = null
        private var periodetype: Periodetype? = null
        private var behandlingsresultat: Behandlingsresultat? = null
        private var mottaker: Mottaker? = null
        private var saksbehandlerEnhet: String? = null
        private var beslutterEnhet: String? = null
        private var behandlingsmetode: Metode = Metode.AUTOMATISK

        internal fun behandlingstatus(behandlingstatus: Behandlingstatus) = apply {
            check(behandlingstatus != AVSLUTTET) { "Bruk funksjonen for å avslutte med behandingsresultat" }
            this.behandlingstatus = behandlingstatus
        }

        internal fun periodetype(periodetype: Periodetype) = apply { this.periodetype = periodetype }
        internal fun behandlingsresultat(behandlingsresultat: Behandlingsresultat?) = apply { this.behandlingsresultat = behandlingsresultat }
        internal fun mottaker(mottaker: Mottaker?) = apply { this.mottaker = mottaker }
        internal fun enheter(saksbehandler: Enhet = AutomatiskEnhet, beslutter: Enhet = AutomatiskEnhet) = apply {
            if (saksbehandler is AutomatiskEnhet && beslutter is AutomatiskEnhet) return@apply
            this.behandlingsmetode = when (beslutter) {
                is FunnetEnhet, ManglendeEnhet -> Metode.TOTRINNS
                is AutomatiskEnhet -> Metode.MANUELL
            }
            this.saksbehandlerEnhet = saksbehandler.id
            this.beslutterEnhet = beslutter.id
        }

        internal fun avslutt(behandlingsresultat: Behandlingsresultat) = apply {
            this.behandlingstatus = AVSLUTTET
            this.behandlingsresultat = behandlingsresultat
        }

        internal fun build(funksjonellTid: OffsetDateTime, hendelsesmetode: Metode): Behandling? {
            val ny = Behandling(
                funksjonellTid = funksjonellTid,
                hendelsesmetode = hendelsesmetode,
                behandlingsmetode = forrige.behandlingsmetode + behandlingsmetode,
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
                mottaker = mottaker ?: forrige.mottaker,
                yrkesaktivitetstype = forrige.yrkesaktivitetstype
            )

            if (ny.funksjoneltLik(forrige)) {
                sikkerLogg.info("Lagrer _ikke_ ny rad. Behandlingen er funksjonelt lik siste rad")
                return null
            }

            if (forrige.behandlingstatus == AVSLUTTET) {
                sikkerLogg.warn("Lagrer _ikke_ ny rad. Behandlingen er allerede avsluttet")
                return null
            }

            return ny
        }

        private companion object {
            private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
            private val Enhet.id
                get() = when (this) {
                    is FunnetEnhet -> id
                    is ManglendeEnhet, AutomatiskEnhet -> null
                }

            private operator fun Metode.plus(ny: Metode) = when (this) {
                Metode.AUTOMATISK -> ny
                Metode.MANUELL -> if (ny == Metode.TOTRINNS) Metode.TOTRINNS else Metode.MANUELL
                Metode.TOTRINNS -> Metode.TOTRINNS
            }
        }
    }
}
