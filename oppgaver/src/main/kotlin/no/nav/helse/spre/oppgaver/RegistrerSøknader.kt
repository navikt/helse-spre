package no.nav.helse.spre.oppgaver

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import java.util.UUID

class RegistrerSøknader(rapidsConnection: RapidsConnection, private val oppgaveDAO: OppgaveDAO, private val publisist: Publisist) : River.PacketListener{

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver", "sendt_søknad_frilans", "sendt_søknad_selvstendig", "sendt_søknad_arbeidsledig")) }
            validate { it.requireKey("@id") }
            validate { it.requireKey("id") }
            validate { it.requireKey("fnr") }
            validate { it.interestedIn("arbeidsgiver.orgnummer") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("Forstod ikke søknad-melding, se sikkerlogg for detaljer")
        sikkerLog.error("Forstod ikke søknadmelding:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        val dokumentId = UUID.fromString(packet["id"].asText())
        val fnr = packet["fnr"].asText()
        val orgnummer = packet["arbeidsgiver.orgnummer"].takeIf(JsonNode::isTextual)?.asText()

        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        Oppgave.nySøknad(hendelseId, dokumentId, fnr, orgnummer, observer)
    }
}
