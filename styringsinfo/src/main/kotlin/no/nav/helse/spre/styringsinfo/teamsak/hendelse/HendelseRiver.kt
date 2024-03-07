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
    eventName: String,
    alternativtEventName: String? = null, // TODO: Fjern meg når #ting heter behandling
    private val valider: (packet: JsonMessage) -> Unit = {},
    private val opprett: (packet: JsonMessage) -> Hendelse,
    rapidsConnection: RapidsConnection,
    private val hendelseDao: HendelseDao,
    private val behandlingshendelseDao: BehandlingshendelseDao): River.PacketListener {
    private val eventNames = listOfNotNull(eventName, alternativtEventName)

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny("@event_name", eventNames)
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.interestedIn(
                    "aktørId",
                    "vedtaksperiodeId",
                    "generasjonId", // TODO: Fjern meg når #ting heter behandling
                    "behandlingId"
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
            sikkerLogg.info("Håndterte ${packet.eventName}. ${it.joinToString { "{}" }}\n\t${packet.toJson()}", *it.toTypedArray())
        }
    }

    // TODO: Fjern generasjonId når #ting heter behandling
    private val JsonMessage.structuredArguments get(): List<StructuredArgument>  =
        listOf("aktørId", "vedtaksperiodeId", "generasjonId", "behandlingId", "@id").mapNotNull { key -> get(key).takeUnless { it.isMissingOrNull() }
            ?.let { keyValue(key.removePrefix("@"), it.asText()) } }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Forsto ikke $eventNames:\n\t${problems.toExtendedReport()}")
    }

    internal companion object {
        private val objectMapper = jacksonObjectMapper()

        private val JsonMessage.eventName get() = this["@event_name"].asText()
        internal val JsonMessage.hendelseId get() = UUID.fromString(this["@id"].asText())
        internal val JsonMessage.opprettet get() = LocalDateTime.parse(this["@opprettet"].asText())
        internal val JsonMessage.vedtaksperiodeId get() = UUID.fromString(this["vedtaksperiodeId"].asText())
        internal val JsonMessage.behandlingId get() = (get("behandlingId").takeUnless { it.isMissingOrNull() } ?: get("generasjonId")).let { UUID.fromString(it.asText()) }
        internal val JsonMessage.saksbehandlerIdent get() = this["saksbehandlerIdent"].asText().takeUnless { it.isBlank() }
        internal val JsonMessage.beslutterIdent get() = this["beslutterIdent"].asText().takeUnless { it.isBlank() }
        internal val JsonMessage.automatiskBehandling get() = this["automatiskBehandling"].asBoolean()
        internal val JsonMessage.blob get() = objectMapper.readTree(toJson())
        internal fun JsonMessage.requireBehandlingId() {
            // TODO: Fjern generasjonId når #ting heter behandling
            if (get("behandlingId").isMissingOrNull()) require("generasjonId") { generasjonId -> UUID.fromString(generasjonId.asText()) }
            else require("behandlingId") { behandlingId -> UUID.fromString(behandlingId.asText()) }
        }
        internal fun JsonMessage.requireVedtaksperiodeId() = require("vedtaksperiodeId") { vedtaksperiodeId -> UUID.fromString(vedtaksperiodeId.asText()) }

    }
}