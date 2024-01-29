package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import java.time.LocalDateTime
import java.util.*

internal class VedtakFattet(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val generasjonId: UUID
) : Hendelse {
    override val type = "vedtak_fattet"

    override fun håndter(behandlingDao: BehandlingDao) {
        val builder = behandlingDao.initialiser(generasjonId) ?: return // Vedtak fattet for noe vi ikke har fått generasjon opprettet for
        val ny = builder
            .behandlingStatus(Behandling.BehandlingStatus.AvsluttetMedVedtak)
            .funksjonellTid(opprettet)
            .build()
            ?: return // Ikke noe endring
        behandlingDao.lagre(ny)
    }
}