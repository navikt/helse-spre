package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.styringsinfo.db.VedtakFattetDao
import no.nav.helse.spre.styringsinfo.domain.VedtakFattet
import java.util.UUID

internal class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakFattetDao: VedtakFattetDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtak_fattet")
                it.requireKey("@id", "hendelser")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("vedtakFattetTidspunkt", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke vedtak_fattet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        log.info("Leser inn og lagrer melding $id")
        val vedtakFattet = packet.toVedtakFattet()
        vedtakFattetDao.lagre(vedtakFattet.patch())
    }
}

internal fun JsonMessage.toVedtakFattet(): VedtakFattet {
    val hendelseIder = this["hendelser"].map { UUID.fromString(it.asText()) }
    return VedtakFattet(
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        vedtakFattetTidspunkt = this["vedtakFattetTidspunkt"].asLocalDateTime(),
        hendelseId = UUID.fromString(this["@id"].asText()),
        hendelser = hendelseIder,
        melding = this.toJson()
    )
}