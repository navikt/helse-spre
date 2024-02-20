package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.styringsinfo.sikkerLogg
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import java.time.LocalDateTime
import java.util.UUID

internal class HendelseRiver(
    private val eventName: String,
    private val valider: (packet: JsonMessage) -> Unit = {},
    private val opprett: (packet: JsonMessage) -> Hendelse,
    rapidsConnection: RapidsConnection,
    private val hendelseDao: HendelseDao,
    private val behandlingshendelseDao: BehandlingshendelseDao): River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", eventName)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.interestedIn(
                    "aktørId",
                    "vedtaksperiodeId",
                    "generasjonId",
                    "saksbehandlerIdent",
                    "beslutterIdent",
                    "automatiskBehandling"
                )
                valider(it)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelse = opprett(packet)
        hendelseDao.lagre(hendelse)
        if (!hendelse.håndter(behandlingshendelseDao)) return // Logger kun hendelser som ble håndtert som en behandlingshendelse
        packet.structuredArguments.let {
            sikkerLogg.info("Håndterte $eventName. ${it.joinToString { "{}" }}\n\t${packet.toJson()}", *it.toTypedArray())
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
        internal val JsonMessage.saksbehandlerIdent get() = this["saksbehandlerIdent"].asText()
        internal val JsonMessage.beslutterIdent get() = this["beslutterIdent"].asText()
        internal val JsonMessage.automatiskBehandling get() = this["automatiskBehandling"].asBoolean()
        internal val JsonMessage.blob get() = objectMapper.readTree(toJson())
        internal fun JsonMessage.requireGenerasjonId() = require("generasjonId") { generasjonId -> UUID.fromString(generasjonId.asText()) }
        internal fun JsonMessage.requireVedtaksperiodeId() = require("vedtaksperiodeId") { vedtaksperiodeId -> UUID.fromString(vedtaksperiodeId.asText()) }
        internal fun JsonMessage.requireSaksbehandlerIdent() = require("saksbehandlerIdent") { saksbehandlerIdent -> saksbehandlerIdent.asText() }
        internal fun JsonMessage.requireAutomatiskBehandling() = require("automatiskBehandling") { automatiskBehandling -> automatiskBehandling.asBoolean() }
    }
}