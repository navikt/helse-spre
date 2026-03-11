package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

private const val EVENT_NAME = "selvstendig_ingen_dager_igjen"

class SelvstendigIngenDagerIgjenRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsgrunnlagClient: ForsikringsgrunnlagClient
) : River.PacketListener {
    init {
        River(rapidsConnection)
            .apply {
                precondition {
                    it.requireValue("@event_name", EVENT_NAME)
                }
                validate {
                    it.requireKey(
                        "skjæringstidspunkt",
                        "fødselsnummer",
                        "@id",
                        "behandlingId"
                    )
                }
            }.register(this)
    }

    override fun onPacket(
        packet: JsonMessage,
        context: MessageContext,
        metadata: MessageMetadata,
        meterRegistry: MeterRegistry
    ) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val behandlingId = BehandlingId(packet["behandlingId"].asUuid())
        val meldingId = packet["@id"].asUuid()

        if (!forsikringsgrunnlagClient.harForsikring(behandlingId)) return

        oppgaveClient.lagOppgave(
            meldingId,
            fødselsnummer,
            Årsak.SykepengerettOpphørtPåGrunnAvMaksdatoAlderEllerDød,
            skjæringstidspunkt
        )
    }

    private fun ForsikringsgrunnlagClient.harForsikring(behandlingId: BehandlingId): Boolean = this.forsikringsgrunnlag(behandlingId) != null
}
