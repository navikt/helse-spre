package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.util.UUID

internal interface BehandlingDao {
    fun initialiser(behandlingId: UUID): Behandling.Builder?
    fun lagre(behandling: Behandling)
    fun hent(behandlingId: UUID): Behandling?
    fun forrigeBehandlingId(saksId: UUID): UUID?
}