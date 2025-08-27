package no.nav.helse.spre.gosys.annullering

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers.withMDC
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import java.util.*
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg

internal class PlanlagtAnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val planlagtAnnulleringDao: PlanlagtAnnulleringDao
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "planlagt_annullering")
            }
            validate { message ->
                message.requireKey(
                    "fødselsnummer",
                    "@id",
                    "yrkesaktivitet",
                    "ident",
                    "begrunnelse"
                )
                message.require("fom", JsonNode::asLocalDate)
                message.require("tom", JsonNode::asLocalDate)
                message.require("@opprettet", JsonNode::asLocalDateTime)
                message.requireArray("vedtaksperioder")
                message.requireArray("årsaker")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error("forstod ikke planlagt_annullering. (se sikkerlogg for melding)")
        sikkerLogg.error("forstod ikke planlagt_annullering:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        val fnr = packet["fødselsnummer"].asText()
        try {
            withMDC(mapOf("meldingsreferanseId" to "$id")) {
                behandleMelding(id, fnr, packet)
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i planlagt annullering-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i planlagt annullering-river: ${err.message}", err)
            throw err
        }
    }

    private fun behandleMelding(meldingId: UUID, fnr: String, packet: JsonMessage) {
        sikkerLogg.info("planlagt_annullering leses inn for fødselsnummer $fnr")
        val planlagtAnnulleringMessage = PlanlagtAnnulleringMessage(
            hendelseId = meldingId,
            fødselsnummer = fnr,
            yrkesaktivitet = packet["yrkesaktivitet"].asText(),
            fom = packet["fom"].asLocalDate(),
            tom = packet["tom"].asLocalDate(),
            saksbehandlerIdent = packet["ident"].asText(),
            årsaker = packet["årsaker"].map { it.asText() },
            begrunnelse = packet["begrunnelse"].asText(),
            vedtaksperioder = packet["vedtaksperioder"].map { UUID.fromString(it.asText()) },
            opprettet = packet["@opprettet"].asLocalDateTime()
        )

        planlagtAnnulleringDao.lagre(planlagtAnnulleringMessage)
        sikkerLogg.info("Lagret planlagt annullering for fødselsnummer $fnr og meldingId $meldingId")
    }
}
