package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.*
import java.util.*

class HåndterInntektsmeldingFørSøknad(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    publisist: Publisist,
) : River.PacketListener {

    private val observer = OppgaveObserver(oppgaveDAO, publisist, rapidsConnection)

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "inntektsmelding_før_søknad") }
            validate { it.requireKey("inntektsmeldingId", "fødselsnummer", "organisasjonsnummer", "fødselsnummer") }
        }.register(this)
    }


    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["inntektsmeldingId"].asText())
        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_før_søknad")) {
            oppgave.håndterLest()
            log.info("Mottok inntektsmelding_før_søknad-event: {}", oppgave.hendelseId)
        }
    }
}

