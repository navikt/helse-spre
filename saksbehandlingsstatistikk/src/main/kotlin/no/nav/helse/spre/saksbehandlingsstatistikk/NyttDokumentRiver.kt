package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import java.util.*
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDateTime
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

internal class NyttDokumentRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.interestedIn("id") }
            validate { it.interestedIn("sendtNav", JsonNode::asLocalDateTime) }
            validate { it.interestedIn("rapportertDato", JsonNode::asLocalDateTime) }
            validate { it.requireAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver")) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        when (packet["@event_name"].textValue()) {
            "sendt_søknad_nav", "sendt_søknad_arbeidsgiver" -> {

                val søknadEvent = NyttDokumentData(
                    hendelseId = UUID.fromString(packet["@id"].textValue()),
                    søknadId = UUID.fromString(packet["id"].textValue()),
                    mottattDato = packet["sendtNav"].asLocalDateTime(),
                    registrertDato = packet["rapportertDato"].asLocalDateTime(),
                )
                søknadDao.upsertSøknad(Søknad.fromEvent(søknadEvent))
                log.info("Søknad med id ${søknadEvent.søknadId} og hendelseId ${søknadEvent.hendelseId} lagret")
            }
            else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
        }
    }
}
