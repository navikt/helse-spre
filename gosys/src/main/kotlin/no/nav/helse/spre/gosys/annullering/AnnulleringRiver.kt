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
                it.requireValue("@event_name", "utbetaling_annullert")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "aktørId",
                    "organisasjonsnummer",
                    "saksbehandlerEpost"
                )
                it.interestedIn(
                    "ident",
                    "epost"
                )
                it.interestedIn("fom", JsonNode::asLocalDate)
                it.interestedIn("tom", JsonNode::asLocalDate)
                it.interestedIn("personFagsystemId", "arbeidsgiverFagsystemId", "fagsystemId")
                it.require("annullertAvSaksbehandler", JsonNode::asLocalDateTime)
                it.requireArray("utbetalingslinjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("grad", "beløp")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        if (listOf(
                packet["personFagsystemId"],
                packet["arbeidsgiverFagsystemId"],
                packet["fagsystemId"]
            ).all { it.isMissingOrNull() }
        ) {
            sikkerLogg.error("Mottok annullering uten fagsystemId. Skipper melding", packet.toJson())
            return
        }
        duplikatsjekkDao.sjekkDuplikat(id) {
            log.info("Oppdaget annullering-event {}", StructuredArguments.keyValue("id", packet["@id"].asText()))
            sikkerLogg.info("utbetaling_annullert lest inn: {}", packet.toJson())
            val annulleringMessage = AnnulleringMessage(id, packet)
            annulleringMediator.opprettAnnullering(annulleringMessage)
        }
    }
}
