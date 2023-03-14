package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class InntektsmeldingIkkeHåndtert(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "inntektsmelding_ikke_håndtert") }
            validate { it.requireKey("inntektsmeldingId") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val inntektsmeldingId = packet["inntektsmeldingId"].asText().let { UUID.fromString(it) }
        val oppgave = oppgaveDAO.finnOppgave(inntektsmeldingId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_ikke_håndtert")) {
            oppgave.håndterInntektsmeldingIkkeHåndtert()
            log.info("Mottok inntektsmelding_ikke_håndtert-event: {}", oppgave.hendelseId)
        }
    }
}

