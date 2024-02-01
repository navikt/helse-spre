package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import java.time.LocalDateTime
import java.util.*

internal class AvsluttetMedVedtak(
    override val id: UUID,
    override val opprettet: LocalDateTime,
    override val blob: JsonNode,
    private val generasjonId: UUID
) : Hendelse {
    override val type = "avsluttet_med_vedtak"

    override fun håndter(behandlingDao: BehandlingDao) {
        val builder = behandlingDao.initialiser(BehandlingId(generasjonId)) ?: return // Avsluttet med vedtak for noe vi ikke har fått generasjon opprettet for
        val ny = builder
            .behandlingstatus(Behandling.Behandlingstatus.Avsluttet)
            .behandlingsresultat(Behandling.Behandlingsresultat.Vedtatt)
            .funksjonellTid(opprettet)
            .build()
            ?: return // Ikke noe endring
        behandlingDao.lagre(ny)
    }
}