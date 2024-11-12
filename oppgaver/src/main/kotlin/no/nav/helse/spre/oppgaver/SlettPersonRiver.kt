package no.nav.helse.spre.oppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class SlettPersonRiver(
    rapidsConnection: RapidsConnection,
    private val oppgaveDAO: OppgaveDAO,
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.requireValue("@event_name", "slett_person") }
            validate { it.requireKey("fødselsnummer") }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        log.info("Fjerner oppgaver knyttet til {}", packet["fødselsnummer"].asText())
        oppgaveDAO.fjernOpgaver(packet["fødselsnummer"].asText())
    }
}

