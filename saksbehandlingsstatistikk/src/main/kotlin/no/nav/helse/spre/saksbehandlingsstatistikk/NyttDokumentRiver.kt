package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

internal class NyttDokumentRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id", "@opprettet", "id") }
            validate { it.requireAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver")) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {

        when (packet["@event_name"].textValue()) {
            "sendt_søknad_nav", "sendt_søknad_arbeidsgiver" -> {
                val nyttDokument = NyttDokumentData.fromJson(packet)
                søknadDao.upsertSøknad(nyttDokument.asSøknad)
                log.info("Søknad med id ${nyttDokument.søknadId} og hendelseId ${nyttDokument.hendelseId} lagret")
            }
            else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
        }
    }


}
