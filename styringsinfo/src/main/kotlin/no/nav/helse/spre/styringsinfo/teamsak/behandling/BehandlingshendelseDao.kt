package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.util.UUID

internal interface BehandlingshendelseDao {
    fun initialiser(behandlingId: BehandlingId): Behandling.Builder?
    fun initialiser(sakId: SakId): Behandling.Builder?
    fun lagre(behandling: Behandling, hendelseId: UUID): Boolean
    fun hent(behandlingId: BehandlingId): Behandling?
    fun hent(sakId: SakId): Behandling?
    fun sisteBehandlingId(sakId: SakId): BehandlingId?
    fun erFørstegangsbehandling(sakId: SakId): Boolean
    fun harHåndtertHendelseTidligere(hendelseId: UUID): Boolean
}