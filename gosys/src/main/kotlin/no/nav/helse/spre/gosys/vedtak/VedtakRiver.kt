package no.nav.helse.spre.gosys.vedtak

import com.fasterxml.jackson.databind.JsonNode
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.spre.gosys.log
import no.nav.helse.rapids_rivers.*
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.UUID

class VedtakRiver(
    private val rapidsConnection: RapidsConnection,
    private val vedtakMediator: VedtakMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
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
        log.info("Oppdaget utbetalingevent {}", keyValue("id", UUID.fromString(packet["@id"].asText())))
        sikkerLogg.info(packet.toJson())

        try {
            vedtakMediator.opprettVedtak(VedtakMessage(packet))
        } catch (error: RuntimeException) {
            log.error("Kritisk feil, stopper lytter og ber om restart, se sikker logg for fullstendig feilmelding")
            sikkerLogg.error("Kritisk feil, stopper lytter og ber om restart", error)
            rapidsConnection.stop()
        }
    }
}

