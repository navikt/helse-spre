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

class SendtSøknadRiver(
    rapidsConnection: RapidsConnection,
    private val sendtSøknadDao: SendtSøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandAny("@event_name", listOf("sendt_søknad_nav", "sendt_søknad_arbeidsgiver"))
                it.requireKey("@id", "fnr")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.interestedIn("korrigerer", "sendtArbeidsgiver", "sendtNav")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke sendt_søknad_nav:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        log.info("Leser inn og lagrer melding $id")
        val sendtSøknad = packet.toSendtSøknad()
        sendtSøknadDao.lagre(sendtSøknad)
    }
}

fun JsonMessage.toSendtSøknad(): SendtSøknad =
    SendtSøknad(
        sendt = this["sendtArbeidsgiver"].takeIf { !it.isNull }?.asLocalDateTime()
            ?: this["sendtNav"].asLocalDateTime(),
        korrigerer = this["korrigerer"].takeIf { !it.isNull }?.asText()?.toUUID(),
        fnr = this["fnr"].asText(),
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        melding = this.toJson()
    )