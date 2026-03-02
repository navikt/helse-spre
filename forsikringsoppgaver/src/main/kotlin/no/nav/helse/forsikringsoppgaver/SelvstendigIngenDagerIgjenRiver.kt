package no.nav.helse.forsikringsoppgaver

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

class SelvstendigIngenDagerIgjenRiver(rapidsConnection: RapidsConnection, oppgaveClient: OppgaveoppretterClient, forsikringsgrunnlagClient: ForsikringsgrunnlagClient) : River.PacketListener {
    init {
        River(rapidsConnection).apply {

        }.register(this)
    }
    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        TODO("Not yet implemented")
    }
}
