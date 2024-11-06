package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection

class InntektsmeldingRiver (
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            validate {  it.demandValue("@event_name", "inntektsmelding") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("inntektsmeldingId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        mappingDao.lagre(
            packet["@id"].toUUID(),
            packet["inntektsmeldingId"].toUUID(),
            DokumentIdType.Inntektsmelding,
            packet["@event_name"].asText(),
            packet["@opprettet"].asLocalDateTime()
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av inntektsmelding  problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av inntektsmelding")
    }
}
