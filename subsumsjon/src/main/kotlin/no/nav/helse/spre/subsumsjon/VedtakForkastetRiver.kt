package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.*


class VedtakForkastetRiver(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtaksperiode_forkastet")
                message.requireKey("@id", "@opprettet", "vedtaksperiodeId", "fødselsnummer", "organisasjonsnummer")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        subsumsjonPublisher(fødselsnummer(packet), vedtak_forkastet(packet))
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av vedtaksperiode_forkastet problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av vedtaksperiode_forkastet")
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["fødselsnummer"].asText()
    }

    private fun vedtak_forkastet(packet: JsonMessage): String {
        return objectMapper.writeValueAsString(
            mutableMapOf(
                "id" to packet["@id"],
                "eventName" to "vedtaksperiodeForkastet",
                "tidsstempel" to packet["@opprettet"],
                "fodselsnummer" to packet["fødselsnummer"],
                "vedtaksperiodeId" to packet["vedtaksperiodeId"],
                "organisasjonsnummer" to packet["organisasjonsnummer"],

                )
        ).also { sikkerLogg.info("sender vedtaksperiode_forkastet: $it") }
    }
}
