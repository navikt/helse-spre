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

class VedtaksperiodeEndretRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "vedtaksperiode_endret") }
            validate { it.requireKey("hendelser") }
            validate { it.interestedIn("forrigeTilstand") }
            validate { it.demandAny("gjeldendeTilstand", listOf("AVVENTER_GODKJENNING", "AVVENTER_GODKJENNING_REVURDERING"))}
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("vedtaksperiode_endret", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val forrigeTilstand = packet["forrigeTilstand"].asText()
        val gjeldendeTilstand = packet["gjeldendeTilstand"].asText()
        packet["hendelser"]
            .map { UUID.fromString(it.asText()) }
            .mapNotNull { oppgaveDAO.finnOppgave(it, observer) }
            .forEach { oppgave ->
                withMDC(mapOf("event" to "vedtaksperiode_endret", "tilstand" to gjeldendeTilstand, "forrigeTilstand" to forrigeTilstand)) {
                    oppgave.håndter(Hendelse.AvventerGodkjenning)
                }
            }
    }
}

