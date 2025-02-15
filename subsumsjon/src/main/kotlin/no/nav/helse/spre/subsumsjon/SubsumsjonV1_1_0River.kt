package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

internal class SubsumsjonV1_1_0River(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "subsumsjon")
                it.requireValue("subsumsjon.versjon", "1.1.0")
            }
            validate {
                it.requireKey(
                    "@id", "@opprettet", "subsumsjon", "subsumsjon.tidsstempel", "subsumsjon.versjon", "subsumsjon.kilde", "subsumsjon.versjonAvKode",
                    "subsumsjon.fodselsnummer", "subsumsjon.lovverk", "subsumsjon.lovverksversjon",
                    "subsumsjon.paragraf", "subsumsjon.input", "subsumsjon.output", "subsumsjon.utfall",
                    "subsumsjon.vedtaksperiodeId", "subsumsjon.behandlingId"
                )
                it.requireKey("subsumsjon.sporing")
                it.interestedIn("subsumsjon.ledd")
                it.interestedIn("subsumsjon.punktum")
                it.interestedIn("subsumsjon.bokstav")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("Feil under validering av subsumsjon:\n$metadata\n${problems.toExtendedReport()}")
        //throw IllegalArgumentException("Feil under validering av subsumsjon: $problems")
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
        val sporing = (packet["subsumsjon.sporing"] as? ObjectNode) ?: objectMapper.createObjectNode()
        sporing.set<ArrayNode>("vedtaksperiode", objectMapper.createArrayNode().apply {
            add(packet["subsumsjon.vedtaksperiodeId"])
        })

        val subsumsjonsmelding = buildMap {
            this["id"] = packet["@id"]
            this["eventName"] = "subsumsjon"
            this["tidsstempel"] = packet["subsumsjon.tidsstempel"]
            this["versjon"] = packet["subsumsjon.versjon"]
            this["kilde"] = packet["subsumsjon.kilde"]
            this["versjonAvKode"] = packet["subsumsjon.versjonAvKode"]
            this["fodselsnummer"] = packet["subsumsjon.fodselsnummer"]
            this["vedtaksperiodeId"] = packet["subsumsjon.vedtaksperiodeId"]
            this["behandlingId"] = packet["subsumsjon.behandlingId"]
            this["sporing"] = sporing
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
