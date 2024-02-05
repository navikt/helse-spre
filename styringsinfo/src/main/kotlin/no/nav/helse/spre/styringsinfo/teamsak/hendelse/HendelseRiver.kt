package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.styringsinfo.sikkerLogg
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingDao
import java.time.LocalDateTime
import java.util.*

internal class HendelseRiver(
    private val eventName: String,
    private val opprett: (packet: JsonMessage) -> Hendelse,
    rapidsConnection: RapidsConnection,
    private val behandlingDao: BehandlingDao): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", eventName)
                it.requireKey("aktørId")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("@id") { generasjonId -> UUID.fromString(generasjonId.asText()) }
                it.require("generasjonId") { generasjonId -> UUID.fromString(generasjonId.asText()) }
                it.interestedIn("vedtaksperiodeId") { generasjonId -> UUID.fromString(generasjonId.asText()) }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        opprett(packet).håndter(behandlingDao)
        packet.structuredArguments.let {
            sikkerLogg.info("Håndterte $eventName. ${it.joinToString { "{}" }}\n\t${packet.toJson()}", it)
        }
    }

    private val JsonMessage.structuredArguments get(): List<StructuredArgument>  =
        listOf("aktørId", "vedtaksperiodeId", "generasjonId", "@id").mapNotNull { key -> get(key).takeUnless { it.isMissingOrNull() }
            ?.let { keyValue(key.removePrefix("@"), it.asText()) } }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Forsto ikke $eventName:\n\t${problems.toExtendedReport()}")
    }

    internal companion object {
        private val objectMapper = jacksonObjectMapper()
        internal val JsonMessage.hendelseId get() = UUID.fromString(this["@id"].asText())
        internal val JsonMessage.opprettet get() = LocalDateTime.parse(this["@opprettet"].asText())
        internal val JsonMessage.vedtaksperiodeId get() = UUID.fromString(this["vedtaksperiodeId"].asText())
        internal val JsonMessage.generasjonId get() = UUID.fromString(this["generasjonId"].asText())
        internal val JsonMessage.blob get() = objectMapper.readTree(toJson())
    }
}