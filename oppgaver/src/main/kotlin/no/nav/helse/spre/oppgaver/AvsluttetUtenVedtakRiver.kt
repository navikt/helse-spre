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

class AvsluttetUtenVedtakRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "avsluttet_uten_vedtak") }
            validate { it.requireKey("hendelser") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("avsluttet_uten_vedtak", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        withMDC(mapOf("event" to "avsluttet_uten_vedtak")) {
            packet["hendelser"]
                .map { UUID.fromString(it.asText()) }
                .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
                .forEach { oppgave ->
                    oppgave.håndter(Hendelse.AvsluttetUtenUtbetaling)
                }
        }
    }
}

