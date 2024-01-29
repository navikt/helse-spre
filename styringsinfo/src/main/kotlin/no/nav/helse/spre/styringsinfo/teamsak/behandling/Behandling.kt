package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.time.LocalDateTime
import java.util.UUID

internal data class Behandling(
    internal val sakId: UUID,
    internal val behandlingId: UUID,
    internal val relatertBehandlingId: UUID?,
    internal val aktørId: String,
    internal val mottattTid: LocalDateTime,          // Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.
    internal val registrertTid: LocalDateTime,       // Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
    internal val funksjonellTid: LocalDateTime,      // Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
    internal val tekniskTid: LocalDateTime,          // Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.
    internal val behandlingStatus: BehandlingStatus
){
    internal enum class BehandlingStatus {
        KomplettFraBruker,
        AvsluttetUtenVedtak,
        AvsluttetMedVedtak,
        BehandlesIInfotrygd
    }

    private val utenMagiskeTimestamps get() = copy(funksjonellTid = LocalDateTime.MIN, tekniskTid = LocalDateTime.MIN)

    class Builder(private val forrige: Behandling) {
        private lateinit var funksjonellTid: LocalDateTime // Denne _må_ alltid settes

        private var behandlingStatus: BehandlingStatus? = null

        internal fun funksjonellTid(funksjonellTid: LocalDateTime) = apply { this.funksjonellTid = funksjonellTid }
        internal fun behandlingStatus(behandlingStatus: BehandlingStatus) = apply { this.behandlingStatus = behandlingStatus }

        internal fun build(): Behandling? {
            val ny = Behandling(
                sakId = forrige.sakId,
                behandlingId = forrige.behandlingId,
                relatertBehandlingId = forrige.relatertBehandlingId,
                aktørId = forrige.aktørId,
                mottattTid = forrige.mottattTid,
                registrertTid = forrige.registrertTid,
                funksjonellTid = funksjonellTid,
                tekniskTid = LocalDateTime.now(),
                behandlingStatus = behandlingStatus ?: forrige.behandlingStatus
            )
            if (forrige.utenMagiskeTimestamps == ny.utenMagiskeTimestamps) return null // Ikke noe ny info
            return ny
        }
    }
}