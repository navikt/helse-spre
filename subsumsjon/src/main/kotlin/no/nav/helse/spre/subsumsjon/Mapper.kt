package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class Mapper(
    private val mappingDao: MappingDao,
    private val subsumsjonsId: String,
    private val fødselsnummer: String,
    private val sporing: JsonNode
) {
    private val sikkerLogg: Logger = LoggerFactory.getLogger("tjenestekall")
    private val sykmeldingIder = mutableListOf<UUID>()
    private val søkandIder = mutableListOf<UUID>()
    private val inntektsmeldingIder = mutableListOf<UUID>()


    fun hentSykmeldingIder(hendelseIder: List<UUID>): List<UUID> {
        val ider = hendelseIder.map { mappingDao.hentSykmeldingId(it) ?: håndterMissingId(it, "sykmelding") }
        sykmeldingIder.addAll(ider)
        return ider
    }

    fun hentSøknadIder(hendelseIder: List<UUID>): List<UUID> {
        val ider = hendelseIder.map { mappingDao.hentSøknadId(it) ?: håndterMissingId(it, "søkand") }
        søkandIder.addAll(ider)
        return ider
    }

    fun hentInntektsmeldingIder(hendelseIder: List<UUID>): List<UUID> {
        val ider = hendelseIder.map { mappingDao.hentInntektsmeldingId(it) ?: håndterMissingId(it, "inntektsmelding") }
        inntektsmeldingIder.addAll(ider)
        return ider
    }

    fun håndterMissingId(hendlseId: UUID, eventName: String): Nothing {
        sikkerLogg.error("Kunne ikke hente dokumentId fra databasen: hendelseId: $hendlseId " +
                "eventName: $eventName ikke funnet. subsumsjonsId: $subsumsjonsId fødselsnummer: $fødselsnummer " +
                "sporing: ${objectMapper.writeValueAsString(sporing)}")
        throw IllegalStateException("Hendelse id: $hendlseId hendelseNavn: $eventName ikke funnet i databasen")
    }

    fun updateSporing() {
        (sporing as ObjectNode).putArray("sykmelding").apply {
            sykmeldingIder.forEach { this.add(it.toString())}
        }

        sporing.putArray("soknad").apply {
            søkandIder.forEach { this.add(it.toString())}
        }
        sporing.putArray("inntektsmelding").apply {
            inntektsmeldingIder.forEach { this.add(it.toString())}
        }
    }
}