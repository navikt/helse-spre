package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class InntektsmeldingerFørSøknadRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "inntektsmelding_før_søknad") }
            validate { it.requireKey("inntektsmeldingId", "fødselsnummer", "organisasjonsnummer", "fødselsnummer") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("inntektsmelding_før_søknad", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["inntektsmeldingId"].asText())
        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_før_søknad")) {
            oppgave.håndterInntektsmeldingFørSøknad()
            log.info("Mottok inntektsmelding_før_søknad-event: {}", oppgave.hendelseId)
        }
    }
}

