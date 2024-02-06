package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import java.time.LocalDateTime
import java.util.UUID

internal interface Hendelse {
    val id: UUID
    val opprettet: LocalDateTime
    val type: String
    val blob: JsonNode
    fun håndter(behandlingDao: BehandlingDao): Boolean
}