package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class InntektsmeldingRiver(rapidsConnection: RapidsConnection, private val oppgaveDAO: OppgaveDAO, private val publisist: Publisist) :
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inntektsmelding") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("inntektsmeldingId") }
            validate { it.requireKey("beregnetInntekt") }
            validate { it.requireKey("virksomhetsnummer") }
            validate { it.requireKey("arbeidstakerFnr") }
            validate { it.interestedIn("refusjon.beloepPrMnd") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("inntektsmelding", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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
