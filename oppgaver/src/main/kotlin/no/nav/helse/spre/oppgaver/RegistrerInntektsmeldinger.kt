package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*

class RegistrerInntektsmeldinger(rapidsConnection: RapidsConnection, private val oppgaveDAO: OppgaveDAO, private val publisist: Publisist) :
    River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.requireKey("inntektsmeldingId") }
            validate { it.requireKey("beregnetInntekt") }
            validate { it.requireKey("virksomhetsnummer") }
            validate { it.requireKey("arbeidstakerFnr") }
            validate { it.requireValue("@event_name", "inntektsmelding") }
            validate { it.interestedIn("refusjon.beloepPrMnd") }
        }.register(this)
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
