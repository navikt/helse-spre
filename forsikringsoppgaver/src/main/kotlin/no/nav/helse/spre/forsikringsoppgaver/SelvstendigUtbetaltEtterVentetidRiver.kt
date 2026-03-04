package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class SelvstendigUtbetaltEtterVentetidRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsgrunnlagClient: ForsikringsgrunnlagClient
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "selvstendig_utbetalt_etter_ventetid")
            }
            validate {
                it.requireKey("fødselsnummer", "behandlingId", "@id")
            }
        }.register(this)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val behandlingId = BehandlingId(packet["behandlingId"].asUuid())
        val forsikringsgrunnlag = forsikringsgrunnlagClient
            .forsikringsgrunnlag(behandlingId)
            ?: error("Fant ikke forsikringsgrunnlag for $behandlingId")

        val meldingId = packet["@id"].asUuid()

        if (forsikringsgrunnlag.fårUtbetaltSykepengerFraDagÉn() && forsikringsgrunnlag.forsikretMedDekningsgrad80Prosent()) {
            oppgaveClient.lagOppgave(
                meldingId,
                fødselsnummer,
                Årsak.UtbetaltFraDagÉnOgDekningsgrad80Prosent
            )
        }
    }

    private fun Forsikringsgrunnlag.forsikretMedDekningsgrad80Prosent(): Boolean = dekningsgrad == 80

    private fun Forsikringsgrunnlag.fårUtbetaltSykepengerFraDagÉn(): Boolean = dag1Eller17 == 1
}
