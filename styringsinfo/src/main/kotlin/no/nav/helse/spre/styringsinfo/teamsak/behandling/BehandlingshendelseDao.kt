package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.util.UUID

internal interface BehandlingshendelseDao {
    fun initialiser(behandlingId: BehandlingId): Behandling.Builder?
    fun initialiser(sakId: SakId): List<Behandling.Builder>
    fun lagre(behandling: Behandling, hendelseId: UUID)
    fun hent(behandlingId: BehandlingId): Behandling?
    fun behandlingIdFraForrigeBehandlingshendelse(sakId: SakId): BehandlingId?
    fun erFørstegangsbehandling(sakId: SakId): Boolean
}