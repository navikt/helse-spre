package no.nav.helse.spre.subsumsjon

import no.nav.helse.rapids_rivers.*

internal class SubsumsjonRiver(
    rapidsConnection: RapidsConnection,
    private val mappingDao: MappingDao,
    private val subsumsjonPublisher: (key: String, value: String) -> Unit
) : River.PacketListener {

    init {
        River(rapidsConnection).apply {
            validate { it.demandValue("@event_name", "subsumsjon") }
            validate { it.requireKey("@id") }
            validate { it.requireKey("@opprettet") }
            validate { it.requireKey("subsumsjon") }
            validate { it.requireKey("subsumsjon.tidsstempel") }
            validate { it.requireKey("subsumsjon.versjon") }
            validate { it.requireKey("subsumsjon.kilde") }
            validate { it.requireKey("subsumsjon.versjonAvKode") }
            validate { it.requireKey("subsumsjon.fodselsnummer") }
            validate { it.requireKey("subsumsjon.sporing") }
            validate { it.requireKey("subsumsjon.lovverk") }
            validate { it.requireKey("subsumsjon.lovverksversjon") }
            validate { it.requireKey("subsumsjon.paragraf") }
            validate { it.requireKey("subsumsjon.input") }
            validate { it.requireKey("subsumsjon.output") }
            validate { it.requireKey("subsumsjon.utfall") }
            validate { it.interestedIn("subsumsjon.ledd") }
            validate { it.interestedIn("subsumsjon.punktum") }
            validate { it.interestedIn("subsumsjon.bokstav") }
            validate { it.interestedIn("subsumsjon.sporing.sykmelding") }
            validate { it.interestedIn("subsumsjon.sporing.soknad") }
            validate { it.interestedIn("subsumsjon.sporing.inntektsmelding") }
        }.register(this)
    }

    override fun onError(problems: MessageProblems, context: MessageContext) {
        sikkerLogg.error("Feil under validering av subsumsjon problems: ${problems.toExtendedReport()} ")
        throw IllegalArgumentException("Feil under validering av subsumsjon: $problems")
    }

    override fun onPacket(packet: JsonMessage, context: MessageContext) {
        log.info("mottatt subsumsjon med id: ${packet["@id"]}")
        subsumsjonPublisher(fødselsnummer(packet), subsumsjonMelding(packet, fødselsnummer(packet)))
    }

    private fun fødselsnummer(packet: JsonMessage): String {
        return packet["subsumsjon.fodselsnummer"].asText()
    }

    private fun subsumsjonMelding(packet: JsonMessage, fødselsnummer: String): String {
        val mapper = Mapper(mappingDao, packet["@id"].asText(), fødselsnummer, packet["subsumsjon.sporing"])

        val internSykmeldingIder = packet["subsumsjon.sporing.sykmelding"].toUUIDs()
        val internSøknadIder = packet["subsumsjon.sporing.soknad"].toUUIDs()
        val internInntektsmeldingIder = packet["subsumsjon.sporing.inntektsmelding"].toUUIDs()

        val sykmeldingIder = mapper.hentSykmeldingIder(internSykmeldingIder).takeIf { it.isNotEmpty() }
            ?: mapper.hentSykmeldingIder(internSøknadIder)
        val søknadIder = mapper.hentSøknadIder(internSøknadIder)
        val inntektsmeldingIder = mapper.hentInntektsmeldingIder(internInntektsmeldingIder)

        log.info("Mapper subsumsjons sporing sykmelding: $internSykmeldingIder til $sykmeldingIder søknad: $internSøknadIder til $søknadIder " +
                "inntektsmelding: $internInntektsmeldingIder til $inntektsmeldingIder")

        mapper.updateSporing()

        return objectMapper.writeValueAsString(
            mutableMapOf<String, Any?>(
                "id" to packet["@id"],
                "eventName" to "subsumsjon",
                "tidsstempel" to packet["subsumsjon.tidsstempel"],
                "versjon" to packet["subsumsjon.versjon"],
                "kilde" to packet["subsumsjon.kilde"],
                "versjonAvKode" to packet["subsumsjon.versjonAvKode"],
                "fodselsnummer" to packet["subsumsjon.fodselsnummer"],
                "sporing" to packet["subsumsjon.sporing"],
                "lovverk" to packet["subsumsjon.lovverk"],
                "lovverksversjon" to packet["subsumsjon.lovverksversjon"],
                "paragraf" to packet["subsumsjon.paragraf"],
                "input" to packet["subsumsjon.input"],
                "output" to packet["subsumsjon.output"],
                "utfall" to packet["subsumsjon.utfall"]
            ).apply {
                put("ledd", packet["subsumsjon.ledd"].takeUnless { it.isMissingOrNull() }?.asInt())
                put("punktum", packet["subsumsjon.punktum"].takeUnless { it.isMissingOrNull() }?.asInt())
                put("bokstav", packet["subsumsjon.bokstav"].takeUnless { it.isMissingOrNull() }?.asText())
            }
        ).also { sikkerLogg.info("sender subsumsjon: $it") }
    }
}
