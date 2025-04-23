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

class InntektsmeldingIkkeHåndtertRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "inntektsmelding_ikke_håndtert") }
            validate {
                it.requireKey("inntektsmeldingId", "speilrelatert")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("inntektsmelding_ikke_håndtert", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val inntektsmeldingId = packet["inntektsmeldingId"].asText().let { UUID.fromString(it) }
        val speilrelatert = packet["speilrelatert"].asBoolean()

        val oppgave = oppgaveDAO.finnOppgave(inntektsmeldingId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_ikke_håndtert", "speilrelatert" to speilrelatert.utfall())) {
            oppgave.håndterInntektsmeldingIkkeHåndtert(speilrelatert)
            log.info("Mottok inntektsmelding_ikke_håndtert-event: {}", oppgave.hendelseId)
        }
    }

    private fun Boolean.utfall() = if (this) "JA" else "NEI"

}

