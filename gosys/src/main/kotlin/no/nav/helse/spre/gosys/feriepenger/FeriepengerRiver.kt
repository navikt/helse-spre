package no.nav.helse.spre.gosys.feriepenger

import com.fasterxml.jackson.databind.JsonNode
import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDate
import com.github.navikt.tbd_libs.rapids_and_rivers.asLocalDateTime
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry
import net.logstash.logback.argument.StructuredArguments.keyValue
import no.nav.helse.spre.gosys.DuplikatsjekkDao
import no.nav.helse.spre.gosys.logg
import no.nav.helse.spre.gosys.sikkerLogg
import java.util.*

class FeriepengerRiver(
    rapidsConnection: RapidsConnection,
    private val duplikatsjekkDao: DuplikatsjekkDao,
    private val feriepengerMediator: FeriepengerMediator
) : River.PacketListener {
    init {
        River(rapidsConnection).apply {
            precondition { it.requireValue("@event_name", "feriepenger_utbetalt") }
            validate {
                it.require("@opprettet", JsonNode::asLocalDateTime)
                it.requireKey("@id", "fødselsnummer", "organisasjonsnummer")
                it.require("fom", JsonNode::asLocalDate)
                it.require("tom", JsonNode::asLocalDate)

                it.requireKey("arbeidsgiverOppdrag.fagsystemId", "arbeidsgiverOppdrag.mottaker", "arbeidsgiverOppdrag.totalbeløp")
                it.requireKey("personOppdrag.fagsystemId", "personOppdrag.mottaker", "personOppdrag.totalbeløp")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val id = UUID.fromString(packet["@id"].asText())
        try {
            duplikatsjekkDao.sjekkDuplikat(id) {
                logg.info("Oppdaget feriepenger-event {}", keyValue("id", id))
                sikkerLogg.info("feriepenger_utbetalt lest inn: {}", packet.toJson())

                val feriepengerMessage = FeriepengerMessage(id, packet)
                feriepengerMediator.opprettFeriepenger(feriepengerMessage)
            }
        } catch (err: Exception) {
            logg.error("Feil i melding $id i feriepenge-river: ${err.message}", err)
            sikkerLogg.error("Feil i melding $id i feriepenge-river: ${err.message}", err)
            throw err
        }
    }

    override fun onError(problems: MessageProblems, context: MessageContext, metadata: MessageMetadata) {
        logg.error(problems.toString())
        sikkerLogg.error(problems.toExtendedReport())
    }
}
