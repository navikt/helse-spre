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
    private lateinit var sykmeldingIder: List<UUID>
    private lateinit var søkandIder: List<UUID>
    private lateinit var inntektsmeldingIder: List<UUID>


    fun mapSykmeldingId(hendlseIder: List<UUID>): List<UUID> {
        return hendlseIder.map { mappingDao.hent(it) ?: håndterMissingId(it, "sykmelding") }.also { sykmeldingIder = it }
    }

    fun mapSøknadId(hendlseIder: List<UUID>): List<UUID> {
        return hendlseIder.map { mappingDao.hent(it) ?: håndterMissingId(it, "søkand") }.also { søkandIder = it }
    }

    fun mapInntektsmelding(hendlseIder: List<UUID>): List<UUID> {
        return hendlseIder.map { mappingDao.hent(it) ?: håndterMissingId(it, "inntektsmelding") }.also { inntektsmeldingIder = it }
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