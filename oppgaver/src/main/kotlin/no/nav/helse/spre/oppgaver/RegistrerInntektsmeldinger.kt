package no.nav.helse.spre.oppgaver

import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.*
import no.nav.helse.rapids_rivers.isMissingOrNull

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
        sjekkUtbetalingTilSøker(packet)
    }

    private fun sjekkUtbetalingTilSøker(packet: JsonMessage) {
        val refusjon = packet["refusjon.beloepPrMnd"].takeUnless { it.isMissingOrNull() }?.asInt()
        val inntekt = packet["beregnetInntekt"].asInt()
        if (refusjon != inntekt) oppgaveDAO.markerSomUtbetalingTilSøker(packet.dokumentId())
    }

    private fun JsonMessage.dokumentId() =
        UUID.fromString(this["inntektsmeldingId"].asText())
}
