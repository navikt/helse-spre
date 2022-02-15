package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.*


class VedtakFattetRiver(
    rapidsConnection: RapidsConnection,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { message ->
                message.demandValue("@event_name", "vedtak_fattet")
                message.requireKey(
                    "@id",
                    "hendelser",
                    "fødselsnummer",
                    "vedtaksperiodeId",
                    "skjæringstidspunkt",
                    "fom",
                    "tom",
                    "organisasjonsnummer",
                    "@opprettet"
                )
                message.interestedIn("utbetalingId")
            }
        }.register(this)
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        subsumsjonPublisher(fødselsnummer(packet), vedtak_fattet(packet))
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av vedtak_fattet problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av vedtak_fattet")
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["fødselsnummer"].asText()
    }

    private fun vedtak_fattet(packet: JsonMessage): String {
        return objectMapper.writeValueAsString(
            mutableMapOf<String, Any?>(
                "id" to packet["@id"],
                "eventName" to "vedtakFattet",
                "tidsstempel" to packet["@opprettet"],
                "hendelser" to packet["hendelser"],
                "fodselsnummer" to packet["fødselsnummer"],
                "vedtaksperiodeId" to packet["vedtaksperiodeId"],
                "skjeringstidspunkt" to packet["skjæringstidspunkt"],
                "fom" to packet["fom"],
                "tom" to packet["tom"],
                "organisasjonsnummer" to packet["organisasjonsnummer"]
            ).apply {
                put("utbetalingId", packet["utbetalingId"].takeUnless { it.isMissingOrNull() }?.asText())
            }
        ).also { sikkerLogg.info("sender vedtak_fattet: $it") }
    }
}