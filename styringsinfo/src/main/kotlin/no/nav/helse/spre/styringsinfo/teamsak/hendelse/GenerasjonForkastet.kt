package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import java.time.LocalDateTime
import java.util.*

internal class GenerasjonForkastet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val generasjonId: UUID
) : Hendelse {
    override val type = "generasjon_forkastet"

    override fun håndter(behandlingDao: BehandlingDao) {
        val builder = behandlingDao.initialiser(BehandlingId(generasjonId)) ?: return
        val ny = builder
            .behandlingStatus(Behandling.BehandlingStatus.BehandlesIInfotrygd)
            .funksjonellTid(opprettet)
            .build()
            ?: return
        behandlingDao.lagre(ny)
    }
}