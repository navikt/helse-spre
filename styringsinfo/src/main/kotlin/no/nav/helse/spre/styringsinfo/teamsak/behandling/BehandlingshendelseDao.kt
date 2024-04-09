package no.nav.helse.spre.styringsinfo.teamsak.behandling

import java.util.*

internal interface BehandlingshendelseDao {
    fun initialiser(behandlingId: BehandlingId): Behandling.Builder
    fun lagre(behandling: Behandling, hendelseId: UUID): Boolean
    fun hent(behandlingId: BehandlingId): Behandling
    fun harLagretBehandingshendelseFor(behandlingId: BehandlingId): Boolean
    fun sisteBehandlingId(sakId: SakId): BehandlingId?
    fun harHåndtertHendelseTidligere(hendelseId: UUID): Boolean
}