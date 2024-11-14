package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class SøknadRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            precondition { it.requireAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver",
                "sendt_søknad_selvstendig", "sendt_søknad_frilans", "sendt_søknad_arbeidsledig")) }
            validate { it.requireKey("@id") }
            validate { it.requireKey("id") }
            validate { it.requireKey("sykmeldingId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        mappingDao.lagre(
            hendelseId = packet["@id"].toUUID(),
            dokumentId = packet["id"].toUUID(),
            dokumentIdType = DokumentIdType.Søknad,
            hendelseNavn = packet["@event_name"].asText(),
            produsert = packet["@opprettet"].asLocalDateTime()
        )
        mappingDao.lagre(
            hendelseId = packet["@id"].toUUID(),
            dokumentId = packet["sykmeldingId"].toUUID(),
            dokumentIdType = DokumentIdType.Sykmelding,
            hendelseNavn = packet["@event_name"].asText(),
            produsert = packet["@opprettet"].asLocalDateTime()
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("Feil under validering av søknad (sendt_søknad_nav, sendt_søknad_arbeidsgiver)  problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av søknad")
    }

}