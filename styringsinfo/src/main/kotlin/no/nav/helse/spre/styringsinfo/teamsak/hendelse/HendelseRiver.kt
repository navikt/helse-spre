package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.logstash.logback.argument.StructuredArgument
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import org.slf4j.Logger
import org.slf4j.LoggerFactory
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
                it.demandAny("@event_name", listOf(eventName, "${eventName}_styringsinfo_replay"))
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                it.interestedIn(
                    "aktørId",
                    "vedtaksperiodeId",
                    "behandlingId"
                )
                valider(it)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelse = opprett(packet)
        if (!hendelseDao.lagre(hendelse)) return packet.sikkerLogg("Har håndtert ${packet.eventName} tidligere")
        // Ikke registrert starten på behandlingen/har ikke noe ny info utover det vi allerede har lagret (funksjonelt lik)/hendelse kommer out of order
        if (!hendelse.håndter(behandlingshendelseDao)) return packet.sikkerLogg("Håndterer _ikke_ ${packet.eventName}")
        packet.sikkerLogg("Håndterte ${packet.eventName}")
    }

    private fun JsonMessage.sikkerLogg(melding: String) = structuredArguments.let { sa ->
        sikkerLogg.info("$melding. ${sa.joinToString { "{}" }}\n\t${toJson()}", *sa)
    }

    private val JsonMessage.structuredArguments get(): Array<StructuredArgument>  =
        listOf("aktørId", "vedtaksperiodeId", "behandlingId", "@id").mapNotNull { key -> get(key).takeUnless { it.isMissingOrNull() }
            ?.let { keyValue(key.removePrefix("@"), it.asText()) } }.toTypedArray()

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Forsto ikke $eventName:\n\t${problems.toExtendedReport()}")
    }

    internal companion object {
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
        private val JsonMessage.eventName get() = this["@event_name"].asText()
        internal val JsonMessage.hendelseId get() = UUID.fromString(this["@id"].asText())
        internal val JsonMessage.opprettet get() = LocalDateTime.parse(this["@opprettet"].asText())
        internal val JsonMessage.vedtaksperiodeId get() = UUID.fromString(this["vedtaksperiodeId"].asText())
        internal val JsonMessage.behandlingId get() = UUID.fromString(this["behandlingId"].asText())
        internal val JsonMessage.blob get() = objectMapper.readTree(toJson())
        internal fun JsonMessage.requireBehandlingId() = require("behandlingId") { behandlingId -> UUID.fromString(behandlingId.asText()) }
        internal fun JsonMessage.requireVedtaksperiodeId() = require("vedtaksperiodeId") { vedtaksperiodeId -> UUID.fromString(vedtaksperiodeId.asText()) }
    }
}