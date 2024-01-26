package no.nav.helse.spre.gosys.annullering

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.rapids_rivers.*
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
            validate {
                it.demandValue("@event_name", "utbetaling_annullert")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "aktørId",
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

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke utbetaling_annullert:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        duplikatsjekkDao.sjekkDuplikat(id) {
            log.info("Oppdaget annullering-event {}", StructuredArguments.keyValue("id", packet["@id"].asText()))
            sikkerLogg.info("utbetaling_annullert lest inn: {}", packet.toJson())
            val annulleringMessage = AnnulleringMessage(id, packet)
            annulleringMediator.opprettAnnullering(annulleringMessage)
        }
    }
}
