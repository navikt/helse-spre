package no.nav.helse.spre.gosys.annullering

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.*

class AnnulleringRiver(
    rapidsConnection: RapidsConnection,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val annulleringMediator: AnnulleringMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "utbetaling_annullert") }
            validate {
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "organisasjonsnummer",
                    "ident",
                    "epost",
                    "personFagsystemId",
                    "arbeidsgiverFagsystemId",
                    "utbetalingId",
                )
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("tidspunkt", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("forstår ikke utbetaling_annullert:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                log.info("Oppdaget annullering-event {}", StructuredArguments.keyValue("id", packet["@id"].asText()))
                sikkerLogg.info("utbetaling_annullert lest inn: {}", packet.toJson())
                val annulleringMessage = AnnulleringMessage(id, packet)
                annulleringMediator.opprettAnnullering(annulleringMessage)
            }
        } catch (err: Exception) {
            log.error("Feil i melding $id i annullering-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i annullering-river: ${err.message}", err)
            throw err
        }
    }
}
