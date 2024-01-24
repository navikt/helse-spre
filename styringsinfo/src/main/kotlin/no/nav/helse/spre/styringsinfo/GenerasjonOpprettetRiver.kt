package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.styringsinfo.db.GenerasjonOpprettetDao
import no.nav.helse.spre.styringsinfo.domain.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.domain.Kilde
import java.util.UUID

internal class GenerasjonOpprettetRiver(
    rapidsConnection: RapidsConnection,
    private val generasjonOpprettetDao: GenerasjonOpprettetDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "generasjon_opprettet")
                it.requireKey("@id",
                        "aktørId",
                        "vedtaksperiodeId",
                        "generasjonId",
                        "type",
                        "kilde.meldingsreferanseId",
                        "kilde.avsender")
                it.require("kilde.innsendt", JsonNode::asLocalDateTime)
                it.require("kilde.registrert", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke generasjon_opprettet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        log.info("Leser inn og lagrer melding $id")
        val generasjonOpprettet = packet.toGenerasjonOpprettet()
        generasjonOpprettetDao.lagre(generasjonOpprettet)
    }
}

internal fun JsonMessage.toGenerasjonOpprettet(): GenerasjonOpprettet {
    return GenerasjonOpprettet(
        aktørId = this["aktørId"].asText(),
        vedtaksperiodeId = UUID.fromString(this["vedtaksperiodeId"].asText()),
        generasjonId = UUID.fromString(this["generasjonId"].asText()),
        type = this["type"].asText(),
        hendelseId = UUID.fromString(this["@id"].asText()),
        kilde = Kilde(
            meldingsreferanseId = UUID.fromString(this["kilde.meldingsreferanseId"].asText()),
            innsendt = this["kilde.innsendt"].asLocalDateTime(),
            registrert = this["kilde.registrert"].asLocalDateTime(),
            avsender = this["kilde.avsender"].asText()
        )
    )
}