package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.UUID

internal class SubsumsjonV1_0_0River(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "subsumsjon")
                it.requireValue("subsumsjon.versjon", "1.0.0")
            }
            validate { it.requireKey("@id") }
            validate { it.requireKey("@opprettet") }
            validate { it.requireKey("subsumsjon") }
            validate { it.requireKey("subsumsjon.tidsstempel") }
            validate { it.requireKey("subsumsjon.versjon") }
            validate { it.requireKey("subsumsjon.kilde") }
            validate { it.requireKey("subsumsjon.versjonAvKode") }
            validate { it.requireKey("subsumsjon.fodselsnummer") }
            validate { it.requireKey("subsumsjon.sporing") }
            validate { it.requireKey("subsumsjon.lovverk") }
            validate { it.requireKey("subsumsjon.lovverksversjon") }
            validate { it.requireKey("subsumsjon.paragraf") }
            validate { it.requireKey("subsumsjon.input") }
            validate { it.requireKey("subsumsjon.output") }
            validate { it.requireKey("subsumsjon.utfall") }
            validate { it.interestedIn("subsumsjon.ledd") }
            validate { it.interestedIn("subsumsjon.punktum") }
            validate { it.interestedIn("subsumsjon.bokstav") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("Feil under validering av subsumsjon problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av subsumsjon: $problems")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        log.info("mottatt subsumsjon med id: ${packet["@id"]}")
        sikkerLogg.info("mottatt subsumsjon med id: ${packet["@id"]}")
        subsumsjonPublisher(fødselsnummer(packet), subsumsjonMelding(packet))
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["subsumsjon.fodselsnummer"].asText()
    }

    private fun subsumsjonMelding(packet: JsonMessage): String {
        val subsumsjonsmelding = buildMap {
            this["id"] = packet["@id"]
            this["eventName"] = "subsumsjon"
            this["tidsstempel"] = packet["subsumsjon.tidsstempel"]
            this["versjon"] = packet["subsumsjon.versjon"]
            this["kilde"] = packet["subsumsjon.kilde"]
            this["versjonAvKode"] = packet["subsumsjon.versjonAvKode"]
            this["fodselsnummer"] = packet["subsumsjon.fodselsnummer"]
            this["sporing"] = mapOf(
                "sykmelding" to emptyList<Nothing>(),
                "soknad" to emptyList(),
                "inntektsmelding" to emptyList()
            )
            this["lovverk"] = packet["subsumsjon.lovverk"]
            this["lovverksversjon"] = packet["subsumsjon.lovverksversjon"]
            this["paragraf"] = packet["subsumsjon.paragraf"]
            this["input"] = packet["subsumsjon.input"]
            this["output"] = packet["subsumsjon.output"]
            this["utfall"] = packet["subsumsjon.utfall"]
            this["ledd"] = packet["subsumsjon.ledd"].takeUnless { it.isMissingOrNull() }?.asInt()
            this["punktum"] = packet["subsumsjon.punktum"].takeUnless { it.isMissingOrNull() }?.asInt()
            this["bokstav"] = packet["subsumsjon.bokstav"].takeUnless { it.isMissingOrNull() }?.asText()
        }

        return objectMapper.writeValueAsString(subsumsjonsmelding).also { sikkerLogg.info("sender subsumsjon: $it") }
    }
}

internal fun JsonNode.toUUID() = UUID.fromString(this.asText())
internal fun JsonNode.toUUIDs() = this.map { it.toUUID() }
