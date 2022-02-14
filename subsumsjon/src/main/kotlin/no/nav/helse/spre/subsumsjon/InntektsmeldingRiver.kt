package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*

class InntektsmeldingRiver (
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inntektsmelding") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("inntektsmeldingId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        mappingDao.lagre(
            packet["@id"].toUUID(),
            packet["inntektsmeldingId"].toUUID(),
            packet["@event_name"].asText(),
            packet["@opprettet"].asLocalDateTime()
        )
    }
}
