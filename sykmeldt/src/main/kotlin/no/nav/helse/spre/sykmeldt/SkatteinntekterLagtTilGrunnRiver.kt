package no.nav.helse.spre.sykmeldt

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.asYearMonth
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.UUID

class SkatteinntekterLagtTilGrunnRiver(rapidsConnection: RapidsConnection, private val forelagteOpplysningerPublisher: ForelagteOpplysningerPublisher) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "skatteinntekter_lagt_til_grunn")
                it.requireKey("vedtaksperiodeId", "behandlingId", "skjæringstidspunkt", "skatteinntekter", "omregnetÅrsinntekt")
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        sikkerlogg.info("Leste melding: ${packet.toJson()}")
        val vedtaksperiodeId = packet["vedtaksperiodeId"].asText().let { UUID.fromString(it) }
        val forelagteOpplysninger = packet.toForelagteOpplysninger()
        forelagteOpplysningerPublisher.sendMelding(vedtaksperiodeId, forelagteOpplysninger)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerlogg.error(problems.toExtendedReport())
    }
}

private fun JsonMessage.toForelagteOpplysninger(): ForelagteOpplysningerMelding {
    return ForelagteOpplysningerMelding(
        vedtaksperiodeId = this["vedtaksperiodeId"].asText().let { UUID.fromString(it) },
        behandlingId = this["behandlingId"].asText().let { UUID.fromString(it) },
        skjæringstidspunkt = this["skjæringstidspunkt"].asLocalDate(),
        tidsstempel = this["@opprettet"].asLocalDateTime(),
        omregnetÅrsinntekt = this["omregnetÅrsinntekt"].asDouble(),
        skatteinntekter = this["skatteinntekter"].map {
            ForelagteOpplysningerMelding.Skatteinntekt(
                måned = it["måned"].asYearMonth(),
                beløp = it["beløp"].asDouble()
            )
        }
    )
}
