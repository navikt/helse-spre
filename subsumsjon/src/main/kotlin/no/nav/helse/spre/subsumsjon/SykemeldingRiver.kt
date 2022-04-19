package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*

internal class SykemeldingRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao,
    private val idValidation: IdValidation
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
        val id = packet["@id"].toUUID()
        val sykmeldingId = packet["sykmeldingId"]
        if (idValidation.isPoisonous(sykmeldingId.asText())) {
            sikkerLogg.warn("Fant poison pill, lagrer ikke sykmelding. HendelseId: $id, [poisonous] dokumentId: $sykmeldingId")
            return
        }
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