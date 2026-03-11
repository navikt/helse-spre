package no.nav.helse.spre.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.math.BigDecimal

private const val EVENT_NAME = "vedtak_fattet"

class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveClient: OppgaveoppretterClient,
    private val forsikringsgrunnlagClient: ForsikringsgrunnlagClient
) : River.PacketListener {
    companion object {
        private val AKSEPTABELT_AVVIK = BigDecimal("30")
    }
    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", EVENT_NAME)
                it.requireValue("yrkesaktivitetstype", "SELVSTENDIG")
                it.requireContains("tags", "Førstegangsbehandling")
            }
            validate {
                it.requireKey("fødselsnummer", "behandlingId", "sykepengegrunnlag", "skjæringstidspunkt", "@id")
            }
        }.register(this)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val fødselsnummer = packet["fødselsnummer"].asText()
        val sykepengegrunnlag = packet["sykepengegrunnlag"].asText().toBigDecimal()
        val skjæringstidspunkt = packet["skjæringstidspunkt"].asLocalDate()
        val behandlingId = BehandlingId(packet["behandlingId"].asUuid())
        teamLogs.info("Mottatt melding $EVENT_NAME. Melding: ${packet.toJson()}")
        val forsikringsgrunnlag = forsikringsgrunnlagClient
            .forsikringsgrunnlag(behandlingId)
            ?: error("Fant ikke forsikringsgrunnlag for $behandlingId")

        val duplikatkontrollId = packet["@id"].asUuid()

        val erJordbruker = forsikringsgrunnlag.premiegrunnlag == null
        if (erJordbruker) {
            teamLogs.info("Behandlingen er jordbruker. Skal ikke lage oppgave i disse tilfellene.\nForsikringsgrunnlag: ${forsikringsgrunnlag.toJsonString()}")
            return
        }

        val avviksprosent = beregnAvvik(sykepengegrunnlag, forsikringsgrunnlag.premiegrunnlag.toBigDecimal())
        if (avviksprosent > AKSEPTABELT_AVVIK) {
            teamLogs.info(
                """Avviket mellom sykepengegrunnlaget og premiegrunnlaget er større enn $AKSEPTABELT_AVVIK %. Oppretter oppgave.
                   Sykepengegrunnlag: ${sykepengegrunnlag.setScale(2)}
                   Premiegrunnlag: ${forsikringsgrunnlag.premiegrunnlag}
                   Avviksprosent: ${avviksprosent.setScale(2)}
                   Forsikringsgrunnlag: ${forsikringsgrunnlag.toJsonString()}
                """.trimIndent()
            )
            oppgaveClient.lagOppgave(
                duplikatkontrollId,
                fødselsnummer,
                Årsak.ForStortAvvikMellomSykepengegrunnlagOgPremiegrunnlag(sykepengegrunnlag, forsikringsgrunnlag.premiegrunnlag.toBigDecimal(), avviksprosent),
                skjæringstidspunkt
            )
        } else {
            teamLogs.info(
                """Avviket mellom sykepengegrunnlaget og premiegrunnlaget er mindre enn eller likt $AKSEPTABELT_AVVIK %. Oppretter ikke oppgave.
                   Sykepengegrunnlag: ${sykepengegrunnlag.setScale(2)}
                   Premiegrunnlag: ${forsikringsgrunnlag.premiegrunnlag}
                   Avviksprosent: ${avviksprosent.setScale(2)}
                   Forsikringsgrunnlag: ${forsikringsgrunnlag.toJsonString()}
                """.trimIndent()
            )
        }
    }
}
