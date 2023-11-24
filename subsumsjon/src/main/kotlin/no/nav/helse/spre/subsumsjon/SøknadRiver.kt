package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.*


class SøknadRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            validate { it.demandAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver",
                "sendt_søknad_selvstendig", "sendt_søknad_frilans", "sendt_søknad_arbeidsledig")) }
            validate { it.requireKey("@id") }
            validate { it.requireKey("id") }
            validate { it.requireKey("sykmeldingId") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
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

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av søknad (sendt_søknad_nav, sendt_søknad_arbeidsgiver)  problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av søknad")
    }

}