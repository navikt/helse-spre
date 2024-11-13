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

class DokumentAliasRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao
) : River.PacketListener {


    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "dokument_alias") }
            validate { it.requireKey("dokumenttype") }
            validate { it.requireKey("hendelsenavn") }
            validate { it.requireKey("intern_dokument_id") }
            validate { it.requireKey("ekstern_dokument_id") }
            validate { it.require("@opprettet", JsonNode::asLocalDateTime) }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val internId = packet["intern_dokument_id"].toUUID()
        val eksternId = packet["ekstern_dokument_id"].toUUID()
        val dokumenttype = when (val type = packet["dokumenttype"].asText().lowercase()) {
            "sykmelding" -> DokumentIdType.Sykmelding
            "søknad" -> DokumentIdType.Søknad
            "inntektsmelding" -> DokumentIdType.Inntektsmelding
            else -> error("ukjent dokumenttype: $type")
        }
        val hendelsenavn = packet["hendelsenavn"].asText()
        val produsert = packet["@opprettet"].asLocalDateTime()

        log.info("knytter ekstern_dokument_id=$eksternId <-> intern_dokument_id=$internId med dokumenttype=$dokumenttype (hendelsenavn=$hendelsenavn)")
        sikkerLogg.info("knytter ekstern_dokument_id=$eksternId <-> intern_dokument_id=$internId med dokumenttype=$dokumenttype (hendelsenavn=$hendelsenavn)")

        mappingDao.lagre(
            hendelseId = internId,
            dokumentId = eksternId,
            dokumentIdType = dokumenttype,
            hendelseNavn = hendelsenavn,
            produsert = produsert
        )
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        sikkerLogg.error("Feil under validering av dokument_alias problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av dokument_alias")
    }

}