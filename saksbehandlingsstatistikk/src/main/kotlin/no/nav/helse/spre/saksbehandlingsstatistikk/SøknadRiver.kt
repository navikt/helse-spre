package no.nav.helse.spre.saksbehandlingsstatistikk

import io.prometheus.client.Counter
import no.nav.helse.rapids_rivers.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

private val counter = Counter.build("sendt_soeknad_event", "Teller antall events av type sendt_søknad").register()

internal class SøknadRiver(
    rapidsConnection: RapidsConnection,
    private val søknadDao: SøknadDao
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id", "@opprettet", "id") }
            validate { it.interestedIn("korrigerer") }
            validate { it.requireAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver")) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val søknadData = SøknadData.fromJson(packet)
        søknadDao.upsertSøknad(søknadData.asSøknad)
        log.info("Søknad med id ${søknadData.søknadId} og hendelseId ${søknadData.hendelseId} lagret")
        counter.inc()
    }

}
