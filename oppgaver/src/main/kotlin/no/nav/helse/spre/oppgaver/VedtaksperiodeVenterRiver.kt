package no.nav.helse.spre.oppgaver

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*

class VedtaksperiodeVenterRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "vedtaksperioder_venter") }
            validate { it.requireKey("@id") }
            validate {
                it.requireArray("vedtaksperioder") {
                    requireKey("hendelser", "organisasjonsnummer", "venterPå.organisasjonsnummer")
                }
            }
        }.register(this)

        // todo: denne riveren er deprecated
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "vedtaksperiode_venter") }
            precondition { it.requireAny("venterPå.venteårsak.hva", listOf("GODKJENNING", "SØKNAD", "INNTEKTSMELDING")) }
            validate { it.requireKey("hendelser", "@id", "organisasjonsnummer", "venterPå.organisasjonsnummer") }
        }.register(object : River.PacketListener {
            override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
                loggUkjentMelding("vedtaksperiode_venter", problems)
            }

            override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
                val meldingId = packet["@id"].asText()
                val venter = objectMapper.readValue<VedtaksperiodeVenterDto>(packet.toJson())
                håndterVedtaksperiodeVenter(meldingId, venter, context)
            }
        })
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("vedtaksperioder_venter", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val meldingId = packet["@id"].asText()
        packet["vedtaksperioder"].forEach { venter ->
            try {
                håndterVedtaksperiodeVenter(meldingId, objectMapper.convertValue<VedtaksperiodeVenterDto>(venter), context)
            } catch (err: Exception) {
                sikkerLog.error("Kunne ikke tolke vedtaksperiode venter: ${err.message}", err)
            }
        }
    }

    private fun håndterVedtaksperiodeVenter(meldingId: String, venter: VedtaksperiodeVenterDto, context: MessageContext) {
        if (venter.venterPå.venteårsak.hva !in listOf("GODKJENNING", "SØKNAD", "INNTEKTSMELDING")) return

        withMDC(mapOf("event" to "vedtaksperiode_venter", "id" to meldingId)) {
            when (venter.venterPå.venteårsak.hva) {
                "INNTEKTSMELDING" -> {
                    if (venter.organisasjonsnummer != venter.venterPå.organisasjonsnummer) {
                        håndter(venter, context) // Utsetter kun om vi venter på IM annen AG
                    }
                }

                "SØKNAD",
                "GODKJENNING" -> håndter(venter, context) // Når vi venter på GODKJENNING/SØKNAD så håndterer vi alltid
            }
        }
    }

    private fun håndter(venter: VedtaksperiodeVenterDto, context: MessageContext) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        venter.hendelser
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave -> oppgave.håndter(Hendelse.VedtaksperiodeVenter) }
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
private data class VedtaksperiodeVenterDto(
    val organisasjonsnummer: String,
    val hendelser: List<UUID>,
    val venterPå: VenterPå
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class VenterPå(
        val organisasjonsnummer: String,
        val venteårsak: Venteårsak
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class Venteårsak(
        val hva : String
    )
}