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

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("inntektsmelding_håndtert", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val inntektsmeldingId = packet["inntektsmeldingId"].asText().let { UUID.fromString(it) }
        val oppgave = oppgaveDAO.finnOppgave(inntektsmeldingId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_håndtert")) {
            oppgave.håndterLest()
            log.info("Mottok inntektsmelding_håndtert-event: {}", oppgave.hendelseId)
        }
    }
}

