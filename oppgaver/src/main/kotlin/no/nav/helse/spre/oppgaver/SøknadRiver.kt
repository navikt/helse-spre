package no.nav.helse.spre.oppgaver

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.UUID

class SøknadRiver(rapidsConnection: RapidsConnection, private val oppgaveDAO: OppgaveDAO, private val publisist: Publisist) : River.PacketListener{

    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver", "sendt_søknad_frilans", "sendt_søknad_selvstendig", "sendt_søknad_arbeidsledig")) }
            validate { it.requireKey("@id") }
            validate { it.requireKey("id") }
            validate { it.requireKey("fnr") }
            validate { it.interestedIn("arbeidsgiver.orgnummer") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("søknad", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val hendelseId = UUID.fromString(packet["@id"].asText())
        val dokumentId = UUID.fromString(packet["id"].asText())
        val fnr = packet["fnr"].asText()
        val orgnummer = packet["arbeidsgiver.orgnummer"].takeIf(JsonNode::isTextual)?.asText()

        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        Oppgave.nySøknad(hendelseId, dokumentId, fnr, orgnummer, observer)
    }
}
