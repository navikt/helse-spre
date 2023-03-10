package no.nav.helse.spre.oppgaver

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import java.util.UUID

class RegistrerSøknader(rapidsConnection: RapidsConnection, private val oppgaveDAO: OppgaveDAO, private val publisist: Publisist) : River.PacketListener{

    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.requireKey("id") }
            validate { it.requireKey("fnr") }
            validate { it.requireKey("arbeidsgiver.orgnummer") }
            validate { it.requireValue("@event_name", "sendt_søknad_nav") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        val dokumentId = UUID.fromString(packet["id"].asText())
        val fnr = packet["fnr"].asText()
        val orgnummer = packet["arbeidsgiver.orgnummer"].asText()

        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        Oppgave.nySøknad(hendelseId, dokumentId, fnr, orgnummer, observer)
    }
}
