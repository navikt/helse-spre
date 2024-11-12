package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*

class SøknadHåndtertRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "søknad_håndtert") }
            validate { it.requireKey("søknadId") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("søknad_håndtert", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val søknadId = packet["søknadId"].asText().let { UUID.fromString(it) }
        val oppgave = oppgaveDAO.finnOppgave(søknadId, observer) ?: return
        withMDC(mapOf("event" to "søknad_håndtert")) {
            oppgave.håndterLest()
            log.info("Mottok søknad_håndtert-event: {}", oppgave.hendelseId)
        }
    }
}

