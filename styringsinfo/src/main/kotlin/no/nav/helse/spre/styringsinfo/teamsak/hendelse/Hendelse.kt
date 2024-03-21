package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import java.time.OffsetDateTime
import java.util.UUID

internal interface Hendelse {
    val id: UUID
    val opprettet: OffsetDateTime
    val type: String
    val data: JsonNode
    fun håndter(behandlingshendelseDao: BehandlingshendelseDao): Boolean
}