package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

internal class Testhendelse(override val id: UUID) : Hendelse {
    override val opprettet: LocalDateTime = LocalDate.EPOCH.atStartOfDay()
    override val type: String = "TULLETYPE"
    override val data: JsonNode = jacksonObjectMapper().createObjectNode().apply { put("test", true) }
    override fun håndter(behandlingshendelseDao: BehandlingshendelseDao) = throw IllegalStateException("Testehendelse skal ikke håndteres")
}