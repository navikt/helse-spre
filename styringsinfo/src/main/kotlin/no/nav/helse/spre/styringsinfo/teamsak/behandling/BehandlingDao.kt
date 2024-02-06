package no.nav.helse.spre.styringsinfo.teamsak.behandling

internal interface BehandlingDao {
    fun initialiser(behandlingId: BehandlingId): Behandling.Builder?
    fun initialiser(sakId: SakId): List<Behandling.Builder>
    fun lagre(behandling: Behandling)
    fun hent(behandlingId: BehandlingId): Behandling?
    fun forrigeBehandlingId(sakId: SakId): BehandlingId?
}