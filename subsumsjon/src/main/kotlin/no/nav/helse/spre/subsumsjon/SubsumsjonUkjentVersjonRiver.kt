package no.nav.helse.spre.subsumsjon

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageMetadata
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import io.micrometer.core.instrument.MeterRegistry

internal class SubsumsjonUkjentVersjonRiver(rapidsConnection: RapidsConnection) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            precondition {
                it.requireValue("@event_name", "subsumsjon")
            }
            validate {
                // forbid versjoner som ER støttet
                it.forbidValue("subsumsjon.versjon", "1.0.0")
                it.forbidValue("subsumsjon.versjon", "1.1.0")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext, metadata: MessageMetadata, meterRegistry: MeterRegistry) {
        val versjon = packet["subsumsjon.versjon"].asText().takeIf(String::isNotBlank) ?: "<INGEN_VERSJON_SATT>"
        sikkerLogg.error("har ingen håndtering av subsumsjoner med versjon $versjon\n${packet.toJson()}")
        error("har ingen håndtering av subsumsjoner med versjon $versjon")
    }
}
