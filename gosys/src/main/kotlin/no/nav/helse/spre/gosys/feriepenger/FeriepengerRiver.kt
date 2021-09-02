package no.nav.helse.spre.gosys.feriepenger

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.*

class FeriepengerRiver(
    rapidsConnection: RapidsConnection,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val feriepengerMediator: FeriepengerMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "feriepenger_utbetalt")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "aktørId",
                    "organisasjonsnummer",
                    "arbeidsgiverOppdrag.fagsystemId",
                )
                it.requireKey("arbeidsgiverOppdrag")
                it.requireArray("arbeidsgiverOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("totalbeløp")
                }
                it.requireKey("personOppdrag")
                it.requireArray("personOppdrag.linjer") {
                    require("fom", JsonNode::asLocalDate)
                    require("tom", JsonNode::asLocalDate)
                    requireKey("totalbeløp")
                }
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        duplikatsjekkDao.sjekkDuplikat(id) {
            log.info("Oppdaget feriepenger-event {}", keyValue("id", id))
            sikkerLogg.info("feriepenger_utbetalt lest inn: {}", packet.toJson())

            val feriepengerMessage = FeriepengerMessage(id, packet)
            feriepengerMediator.opprettFeriepenger(feriepengerMessage)
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
        sikkerLogg.error(problems.toExtendedReport())
    }
}
