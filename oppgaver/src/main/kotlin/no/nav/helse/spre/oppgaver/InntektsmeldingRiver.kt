package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*

class InntektsmeldingRiver(rapidsConnection: RapidsConnection, private val oppgaveDAO: OppgaveDAO, private val publisist: Publisist) :
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "inntektsmelding") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("inntektsmeldingId") }
            validate { it.requireKey("beregnetInntekt") }
            validate { it.requireKey("virksomhetsnummer") }
            validate { it.requireKey("arbeidstakerFnr") }
            validate { it.interestedIn("refusjon.beloepPrMnd") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("inntektsmelding", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        val dokumentId = packet.dokumentId()
        val fnr = packet["arbeidstakerFnr"].asText()
        val organisasjonsnummer = packet["virksomhetsnummer"].asText()

        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        Oppgave.nyInntektsmelding(hendelseId, dokumentId, fnr, organisasjonsnummer, observer)
    }

    private fun JsonMessage.dokumentId() =
        UUID.fromString(this["inntektsmeldingId"].asText())
}
