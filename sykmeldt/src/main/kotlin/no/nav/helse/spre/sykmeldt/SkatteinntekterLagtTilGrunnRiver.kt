package no.nav.helse.spre.sykmeldt

import no.nav.helse.rapids_rivers.*

class SkatteinntekterLagtTilGrunnRiver(rapidsConnection: RapidsConnection) : River.PacketListener {
    var lestMelding = false

    init {
        River(rapidsConnection).apply {
            validate {
                it.demandValue("@event_name", "skatteinntekter_lagt_til_grunn")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        lestMelding = true
        sikkerlogg.info("Leste melding: ${packet.toJson()}")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerlogg.error(problems.toExtendedReport())
    }
}
