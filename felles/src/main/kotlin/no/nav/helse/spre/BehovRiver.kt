package no.nav.helse.spre

import com.github.navikt.tbd_libs.rapids_and_rivers.JsonMessage
import com.github.navikt.tbd_libs.rapids_and_rivers.River
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageContext
import com.github.navikt.tbd_libs.rapids_and_rivers_api.MessageProblems
import com.github.navikt.tbd_libs.rapids_and_rivers_api.RapidsConnection
import org.slf4j.LoggerFactory

class BehovRiver(
    rapidsConnection: RapidsConnection,
    private val behov: String,
    private val validation: (JsonMessage) -> Unit = {},
    private val packetListener: (JsonMessage, MessageContext) -> Unit
) : River.PacketValidation, River.PacketListener {
    private companion object {
        private val log = LoggerFactory.getLogger(BehovRiver::class.java)
        private val sikkerLogg = LoggerFactory.getLogger("tjenestekall")
    }

    init {
        River(rapidsConnection).apply {
            validate(this@BehovRiver)
            validate(validation)
        }.register(this)
    }

    override fun validate(message: JsonMessage) {
        message.demandAll("@behov", listOf(behov))
        message.rejectKey("@final", "@løsning")
        message.requireKey("fødselsnummer", "organisasjonsnummer")
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        log.error("Forstod ikke $behov (se sikkerLog for detaljer)")
        sikkerLogg.error("Forstod ikke $behov: ${problems.toExtendedReport()}")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        packetListener(packet, context)
    }
}