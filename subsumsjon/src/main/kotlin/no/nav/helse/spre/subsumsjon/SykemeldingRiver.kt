package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*

internal class SykemeldingRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao,
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("ny_søknad", "ny_søknad_frilans", "ny_søknad_selvstendig", "ny_søknad_arbeidsledig")) }
            validate { it.requireKey("@id") }
            validate { it.requireKey("sykmeldingId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = packet["@id"].toUUID()
        val sykmeldingId = packet["sykmeldingId"]
        mappingDao.lagre(
            id,
            sykmeldingId.toUUID(),
            DokumentIdType.Sykmelding,
            packet["@event_name"].asText(),
            packet["@opprettet"].asLocalDateTime()
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av ny_søknad problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av ny_søknad")
    }
}
