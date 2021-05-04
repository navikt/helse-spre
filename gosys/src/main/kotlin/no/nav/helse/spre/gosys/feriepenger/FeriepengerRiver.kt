package no.nav.helse.spre.gosys.feriepenger

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.gosys.log
import no.nav.helse.spre.gosys.sikkerLogg

class FeriepengerRiver(
    rapidsConnection: RapidsConnection,
    private val feriepengerMediator: FeriepengerMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "utbetaling_endret")
                it.demandValue("type", "FERIEPENGER")
                it.demandValue("gjeldendeStatus", "UTBETALT")
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
        log.info("Oppdaget feriepenger-event {}", StructuredArguments.keyValue("id", packet["@id"].asText()))
        sikkerLogg.info(packet.toJson())

        val feriepengerMessage = FeriepengerMessage(packet)
        feriepengerMediator.opprettFeriepenger(feriepengerMessage)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error(problems.toString())
        sikkerLogg.error(problems.toExtendedReport())
    }
}
