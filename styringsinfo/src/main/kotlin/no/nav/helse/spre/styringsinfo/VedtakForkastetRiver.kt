package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.spre.styringsinfo.db.VedtakForkastetDao
import no.nav.helse.spre.styringsinfo.domain.VedtakForkastet
import java.util.UUID

internal class VedtakForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val vedtakForkastetDao: VedtakForkastetDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "vedtaksperiode_forkastet")
                it.requireKey("@id", "hendelser")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("@opprettet", JsonNode::asLocalDateTime)
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke vedtak_forkastet:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        log.info("Leser inn og lagrer melding $id")
        val vedtakForkastet = packet.toVedtakForkastet()
        vedtakForkastetDao.lagre(vedtakForkastet.patch())
    }
}

internal fun JsonMessage.toVedtakForkastet(): VedtakForkastet {
    val hendelseIder = this["hendelser"].map { UUID.fromString(it.asText()) }
    return VedtakForkastet(
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        forkastetTidspunkt = this["@opprettet"].asLocalDateTime(),
        hendelseId = UUID.fromString(this["@id"].asText()),
        hendelser = hendelseIder,
        melding = this.toJson()
    )
}