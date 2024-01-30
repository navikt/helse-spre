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
internal class Versjon private constructor(val versjon: String) {
    init { check(versjon.matches("\\d\\.\\d\\.\\d".toRegex())) { "Ugyldig versjon $versjon" } }
    override fun equals(other: Any?) = other is Versjon && this.versjon == other.versjon
    override fun hashCode() = versjon.hashCode()
    override fun toString() = versjon
    internal companion object {
        internal fun of(versjon: String) = Versjon(versjon)
    }
}

internal data class Behandling(
    internal val sakId: SakId,
    internal val behandlingId: BehandlingId,
    internal val relatertBehandlingId: BehandlingId?,
    internal val aktørId: String,
    internal val mottattTid: LocalDateTime,          // Tidspunktet da behandlingen oppstår (eks. søknad mottas). Dette er starten på beregning av saksbehandlingstid.
    internal val registrertTid: LocalDateTime,       // Tidspunkt da behandlingen første gang ble registrert i fagsystemet. Ved digitale søknader bør denne være tilnærmet lik mottattTid.
    internal val funksjonellTid: LocalDateTime,      // Tidspunkt for siste endring på behandlingen. Ved første melding vil denne være lik registrertTid.
    internal val tekniskTid: LocalDateTime,          // Tidspunktet da fagsystemet legger hendelsen på grensesnittet/topicen.
    internal val behandlingStatus: BehandlingStatus,
    internal val versjon: Versjon = NåværendeVersjon
){
    internal enum class BehandlingStatus {
        KomplettFraBruker,
        AvsluttetUtenVedtak,
        AvsluttetMedVedtak,
        BehandlesIInfotrygd
    }

    private fun funksjoneltLik(other: Behandling): Boolean {
        return copy(funksjonellTid = MIN, tekniskTid = MIN, versjon = Versjonløs) == other.copy(funksjonellTid = MIN, tekniskTid = MIN, versjon = Versjonløs)
    }

    private companion object {
        val Versjonløs = Versjon.of("0.0.0")
        val NåværendeVersjon = Versjon.of("0.0.1")
    }

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
            if (ny.funksjoneltLik(forrige)) return null // Ikke noe ny info
            return ny
        }
    }
}