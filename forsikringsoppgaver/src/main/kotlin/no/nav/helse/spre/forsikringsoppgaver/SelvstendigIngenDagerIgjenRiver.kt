package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class SelvstendigIngenDagerIgjenRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "selvstendig_ingen_dager_igjen")
            }
            validate {
                it.requireKey(
                    "skjæringstidspunkt", "fødselsnummer", "@id"
                )
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()


        val meldingId = packet["@id"].asUuid()

        oppgaveClient.lagOppgave(
            meldingId,
            fødselsnummer,
            Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød,
            skjæringstidspunkt,
        )
    }
}

