package no.nav.helse.spre.saksbehandlingsstatistikk

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.spre.saksbehandlingsstatistikk.Dokument.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("saksbehandlingsstatistikk")

internal class NyttDokumentRiver(rapidsConnection: RapidsConnection, private val dokumentDao: DokumentDao) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            validate { it.requireKey("@id") }
            validate { it.interestedIn("sykmeldingId", ::erUuid) }
            validate { it.interestedIn("inntektsmeldingId", ::erUuid) }
            validate { it.interestedIn("id") }
            validate { it.requireAny("@event_name", listOf("ny_søknad", "inntektsmelding", "sendt_søknad_nav", "sendt_søknad_arbeidsgiver")) }
        }.register(this)
    }

    private fun erUuid(jsonNode: JsonNode) = UUID.fromString(jsonNode.asText())

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val hendelseId = UUID.fromString(packet["@id"].textValue())

        when (packet["@event_name"].textValue()) {
            "inntektsmelding" -> {
                val dokumentId = UUID.fromString(packet["inntektsmeldingId"].textValue())
                dokumentDao.opprett(Hendelse(dokumentId, hendelseId, Inntektsmelding))
            }
            "ny_søknad" -> {
                val sykmeldingId = UUID.fromString(packet["sykmeldingId"].textValue())
                dokumentDao.opprett(Hendelse(sykmeldingId, hendelseId, Sykmelding))
            }
            "sendt_søknad_nav", "sendt_søknad_arbeidsgiver" -> {
                val sykmeldingId = UUID.fromString(packet["sykmeldingId"].textValue())
                dokumentDao.opprett(Hendelse(sykmeldingId, hendelseId, Sykmelding))
                val søknadId = UUID.fromString(packet["id"].textValue())
                dokumentDao.opprett(Hendelse(søknadId, hendelseId, Søknad))
            }
            else -> throw IllegalStateException("Ukjent event (etter whitelist :mind_blown:)")
        }

        log.info("Dokument med hendelse $hendelseId lagret")
    }
}
