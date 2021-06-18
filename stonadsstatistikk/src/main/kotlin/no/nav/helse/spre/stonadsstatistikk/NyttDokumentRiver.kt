package no.nav.helse.spre.stonadsstatistikk

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.util.*

private val log: Logger = LoggerFactory.getLogger("stonadsstatistikk")

internal class NyttDokumentRiver(rapidsConnection: RapidsConnection, private val dokumentDao: DokumentDao) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.interestedIn("inntektsmeldingId", "id", "sykmeldingId") }
            validate { it.requireAny("@event_name", listOf("inntektsmelding", "sendt_søknad_nav", "sendt_søknad_arbeidsgiver")) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        try {
            if (packet["sykmeldingId"].textValue() == "1610050814kipe02438.1") return
        }catch (e:Exception) {
            //ignore
        }
        val hendelseId = UUID.fromString(packet["@id"].textValue())

        when (packet["@event_name"].textValue()) {
            "inntektsmelding" -> {
                val dokumentId = UUID.fromString(packet["inntektsmeldingId"].textValue())
                dokumentDao.opprett(Hendelse(dokumentId, hendelseId, Dokument.Inntektsmelding))
            }
            "sendt_søknad_nav", "sendt_søknad_arbeidsgiver" -> {
                val sykmeldingId = UUID.fromString(packet["sykmeldingId"].textValue())
                dokumentDao.opprett(Hendelse(sykmeldingId, hendelseId, Dokument.Sykmelding))
                val søknadId = UUID.fromString(packet["id"].textValue())
                dokumentDao.opprett(Hendelse(søknadId, hendelseId, Dokument.Søknad))
            }
            else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
        }

        log.info("Dokument med hendelse $hendelseId lagret")
    }
}

enum class Dokument {
    Sykmelding, Inntektsmelding, Søknad
}
