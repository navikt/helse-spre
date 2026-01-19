package no.nav.helse.spre.styringsinfo.teamsak.hendelse

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.isMissingOrNull
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.offsetDateTimeOslo
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.UUID

internal class HendelseRiver(
    private val eventName: String,
    private val valider: (packet: JsonMessage) -> Unit = {},
    private val opprett: (packet: JsonMessage) -> Hendelse,
    rapidsConnection: RapidsConnection,
    private val hendelseDao: HendelseDao,
    private val behandlingshendelseDao: BehandlingshendelseDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireAny("@event_name", listOf(eventName, "${eventName}_styringsinfo_replay")) }
            validate {
                it.require("@opprettet") { opprettet -> opprettet.tidspunkt }
                it.require("@id") { id -> UUID.fromString(id.asText()) }
                // Interessted in fordi vi logger alle som MDC-verdier om verdiene er satt selv om det er tilfeller ingen er satt
                // De fleste meldinger har kun `behandlingId`. behandling_opprettet har alle disse 3 verdiene, mens vedtaksperioder_venter har ingen ettersom de der er i en liste av fler perioder.
                it.interestedIn("vedtaksperiodeId", "behandlingId", "yrkesaktivitetstype")
                valider(it)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        withMDC(packet.mdcValues) {
            try {
                håndterHendelse(packet)
            } catch (throwable: Throwable) {
                // Logger og kaster feil videre slik at vi ikke commiter offset. Men blir stående på den feilende meldingen.
                packet.sikkerLogg("Feil ved håndtering av ${packet.eventName}: ${throwable.message}", throwable)
                throw throwable
            }
        }
    }

    private fun håndterHendelse(packet: JsonMessage) {
        val hendelse = opprett(packet)
        if (hendelse.ignorer(behandlingshendelseDao)) return packet.sikkerLogg("Ignorer ${packet.eventName}")
        if (behandlingshendelseDao.harHåndtertHendelseTidligere(hendelse.id)) return packet.sikkerLogg("Har håndtert ${packet.eventName} tidligere")
        hendelseDao.lagre(hendelse)
        // Har ikke noe ny info utover det vi allerede har lagret (funksjonelt lik)/hendelse kommer out of order
        if (!hendelse.håndter(behandlingshendelseDao)) return packet.sikkerLogg("Håndterer _ikke_ ${packet.eventName}")
        packet.sikkerLogg("Håndterte ${packet.eventName}")
    }

    private fun JsonMessage.sikkerLogg(melding: String, throwable: Throwable? = null) =
        if (throwable == null) sikkerLogg.info("$melding\n\t${toJson()}")
        else sikkerLogg.error("$melding\n\t${toJson()}", throwable)

    private val JsonMessage.mdcValues
        get() = listOf("vedtaksperiodeId", "behandlingId", "yrkesaktivitetstype", "@id", "@event_name")
            .associate { key -> key.removePrefix("@") to get(key) }
            .mapValues { (_, value) -> value.takeUnless { it.isMissingOrNull() }?.asText() }
            .filterValues { it != null }
            .mapValues { (_, value) -> value!! }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("Forsto ikke $eventName:\n\t${problems.toExtendedReport()}")
    }

    internal companion object {
        private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
        private val objectMapper = jacksonObjectMapper()
        private val JsonMessage.eventName get() = this["@event_name"].asText()

        internal val JsonMessage.hendelseId get() = UUID.fromString(this["@id"].asText())
        internal val JsonNode.tidspunkt get() = asText().offsetDateTimeOslo
        internal val JsonMessage.opprettet get() = get("@opprettet").tidspunkt
        internal val JsonMessage.vedtaksperiodeId get() = UUID.fromString(this["vedtaksperiodeId"].asText())
        internal val JsonMessage.behandlingId get() = UUID.fromString(this["behandlingId"].asText())
        internal val JsonMessage.yrkesaktivitetstype get() = this["yrkesaktivitetstype"].asText().also { check(it.isNotBlank()) }
        internal val JsonMessage.blob get() = objectMapper.readTree(toJson())

        internal fun JsonMessage.requireBehandlingId() = require("behandlingId") { behandlingId -> UUID.fromString(behandlingId.asText()) }
        internal fun JsonMessage.requireVedtaksperiodeId() = require("vedtaksperiodeId") { vedtaksperiodeId -> UUID.fromString(vedtaksperiodeId.asText()) }
        internal fun JsonMessage.requireYrkesaktivitetstype() = require("yrkesaktivitetstype") { yrkesaktivitetstype -> yrkesaktivitetstype.asText() }
    }
}
