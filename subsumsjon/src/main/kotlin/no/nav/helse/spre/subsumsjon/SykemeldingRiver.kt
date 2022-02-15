package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*

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

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av ny_søknad  problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av ny_søknad")
    }
}


