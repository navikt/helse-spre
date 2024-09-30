package no.nav.helse.spre.sykmeldt

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import java.util.UUID

class SkatteinntekterLagtTilGrunnRiver(rapidsConnection: RapidsConnection, private val forelagteOpplysningerPublisher: ForelagteOpplysningerPublisher) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "skatteinntekter_lagt_til_grunn")
                it.requireKey("vedtaksperiodeId", "behandlingId", "skatteinntekter")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        sikkerlogg.info("Leste melding: ${packet.toJson()}")
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().let { UUID.fromString(it) }
        val forelagteOpplysninger = packet.toForelagteOpplysninger()
        forelagteOpplysningerPublisher.sendMelding(vedtaksperiodeId, forelagteOpplysninger)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error(problems.toExtendedReport())
    }
}

private fun JsonMessage.toForelagteOpplysninger(): ForelagteOpplysningerMelding {
    return ForelagteOpplysningerMelding(
        vedtaksperiodeId = this["vedtaksperiodeId"].asText().let { UUID.fromString(it) },
        behandlingId = this["behandlingId"].asText().let { UUID.fromString(it) },
        tidsstempel = this["@opprettet"].asLocalDateTime(),
        skatteinntekter = this["skatteinntekter"].map {
            ForelagteOpplysningerMelding.Skatteinntekt(
                måned = it["måned"].asYearMonth(),
                beløp = it["beløp"].asDouble()
            )
        }
    )
}
