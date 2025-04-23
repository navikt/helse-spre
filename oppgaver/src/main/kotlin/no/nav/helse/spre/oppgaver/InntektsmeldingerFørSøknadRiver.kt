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

class InntektsmeldingerFørSøknadRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
    private val publisist: Publisist,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "inntektsmelding_før_søknad") }
            validate { it.requireKey("inntektsmeldingId", "fødselsnummer", "organisasjonsnummer") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        loggUkjentMelding("inntektsmelding_før_søknad", problems)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val observer = OppgaveObserver(oppgaveDAO, publisist, context)
        val hendelseId = UUID.fromString(packet["inntektsmeldingId"].asText())
        val oppgave = oppgaveDAO.finnOppgave(hendelseId, observer) ?: return
        withMDC(mapOf("event" to "inntektsmelding_før_søknad")) {
            oppgave.håndterInntektsmeldingFørSøknad()
            log.info("Mottok inntektsmelding_før_søknad-event: {}", oppgave.hendelseId)
        }
    }
}

