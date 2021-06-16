package no.nav.helse.spre.saksbehandlingsstatistikk

import io.prometheus.client.Counter
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

private val counter = Counter.build("sendt_soeknad_event", "Teller antall events av type sendt_søknad").register()

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
                counter.inc()
            }
            else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
        }
    }


}
