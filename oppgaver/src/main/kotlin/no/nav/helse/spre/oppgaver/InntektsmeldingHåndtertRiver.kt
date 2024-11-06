package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class InntektsmeldingHåndtertRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inntektsmelding_håndtert") }
            validate { it.requireKey("inntektsmeldingId") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("inntektsmelding_håndtert", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val inntektsmeldingId = packet["inntektsmeldingId"].asText().let { UUID.fromString(it) }
        val oppgave = oppgaveDAO.finnOppgave(inntektsmeldingId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_håndtert")) {
            oppgave.håndterLest()
            log.info("Mottok inntektsmelding_håndtert-event: {}", oppgave.hendelseId)
        }
    }
}

