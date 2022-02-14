package no.nav.helse.spre.subsumsjon.no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.subsumsjon.MappingDao
import java.util.*

class SykemeldingRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "ny_søknad") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("sykmeldingId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        mappingDao.lagre(
            packet["@id"].toUUID(),
            packet["sykmeldingId"].toUUID(),
            packet["@event_name"].asText(),
            packet["@opprettet"].asLocalDateTime()
        )
    }
}

private fun JsonNode.toUUID() = UUID.fromString(this.asText())
