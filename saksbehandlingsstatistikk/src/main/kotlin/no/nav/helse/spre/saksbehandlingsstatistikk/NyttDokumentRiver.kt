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
        val hendelseId = UUID.fromString(packet["@id"].textValue())

        when (packet["@event_name"].textValue()) {
            "sendt_søknad_nav", "sendt_søknad_arbeidsgiver" -> {
                val søknadId = UUID.fromString(packet["id"].textValue())
                val mottattDato = packet["sendtNav"].asLocalDateTime()
                val registrertDato = packet["rapportertDato"].asLocalDateTime()
                val saksbehandlerIdent = ""
                søknadDao.opprett(Søknad(hendelseId, søknadId, mottattDato, registrertDato, saksbehandlerIdent))
                log.info("Søknad med id $søknadId og hendelseId $hendelseId lagret")
            }
            else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
        }
    }
}
