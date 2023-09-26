package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.JsonNode
import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageContext
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.rapids_rivers.RapidsConnection
import no.nav.helse.rapids_rivers.River
import no.nav.helse.rapids_rivers.asLocalDate
import no.nav.helse.rapids_rivers.asLocalDateTime
import no.nav.helse.rapids_rivers.toUUID
import java.util.UUID

internal class SendtSøknadNavRiver(
    rapidsConnection: RapidsConnection,
    private val sendtSøknadDao: SendtSøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "sendt_søknad_nav")
                it.requireKey("@id", "fnr")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("sendtNav", JsonNode::asLocalDateTime)
                it.interestedIn("korrigerer")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke sendt_søknad_nav:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        log.info("Leser inn og lagrer melding $id")
        val sendtSøknad = packet.toSendtSøknadNav()
        sendtSøknadDao.lagre(sendtSøknad)
    }
}

internal fun JsonMessage.toSendtSøknadNav(): SendtSøknad =
    SendtSøknad(
        sendt = this["sendtNav"].asLocalDateTime(),
        korrigerer = this["korrigerer"].takeIf { !it.isNull }?.asText()?.toUUID(),
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        hendelseId = UUID.fromString(this["@id"].asText()),
        melding = this.toJson()
    )