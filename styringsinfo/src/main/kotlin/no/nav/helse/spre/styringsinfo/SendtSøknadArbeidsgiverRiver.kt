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
import no.nav.helse.spre.styringsinfo.db.SendtSøknadDao
import no.nav.helse.spre.styringsinfo.domain.SendtSøknad
import java.util.UUID

internal class SendtSøknadArbeidsgiverRiver(
    rapidsConnection: RapidsConnection,
    private val sendtSøknadDao: SendtSøknadDao,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "sendt_søknad_arbeidsgiver")
                it.requireKey("@id", "fnr") // TODO: Vurdere å fjerne fnr
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)
                it.require("sendtArbeidsgiver", JsonNode::asLocalDateTime)
                it.interestedIn("korrigerer")
            }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("forstår ikke sendt_søknad_arbeidsgiver:\n${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        val id = UUID.fromString(packet["@id"].asText())
        log.info("Leser inn og lagrer melding $id")
        val sendtSøknad = packet.toSendtSøknadArbeidsgiver()
        sendtSøknadDao.lagre(sendtSøknad.patch())
    }
}

internal fun JsonMessage.toSendtSøknadArbeidsgiver(): SendtSøknad =
    SendtSøknad(
        sendt = this["sendtArbeidsgiver"].asLocalDateTime(),
        korrigerer = this["korrigerer"].takeIf { !it.isNull }?.asText()?.toUUID(),
        fom = this["fom"].asLocalDate(),
        tom = this["tom"].asLocalDate(),
        hendelseId = UUID.fromString(this["@id"].asText()),
        melding = this.toJson()
    )