package no.nav.helse.spre.gosys

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.spre.gosys.log
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.UUID

class ProduserteVedtakTilbakeinnlesingRiver(
    rapidsConnection: RapidsConnection
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            TODO("Skriv koden")
            validate {
                it.requireValue("@event_name", "utbetalt")
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey(
                    "@id",
                    "fødselsnummer",
                    "aktørId",
                    "organisasjonsnummer",
                    "gjenståendeSykedager",
                    "utbetalt",
                    "ikkeUtbetalteDager",
                    "sykepengegrunnlag",
                    "automatiskBehandling",
                    "godkjentAv"
                )
                it.interestedIn("maksdato", JsonNode::asLocalDate)
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("noe tekst her {}", keyValue("id", UUID.fromString(packet["@id"].asText())))
        sikkerLogg.info(packet.toJson())
    }
}

