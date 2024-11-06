package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import java.util.*

class AvsluttetMedVedtakRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "avsluttet_med_vedtak") }
            validate { it.requireKey("hendelser") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        loggUkjentMelding("avsluttet_med_vedtak", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        withMDC(mapOf("event" to "avsluttet_med_vedtak")) {
            packet["hendelser"]
                .map { UUID.fromString(it.asText()) }
                .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
                .forEach { oppgave ->
                    oppgave.håndter(Hendelse.Avsluttet)
                }
        }
    }
}

