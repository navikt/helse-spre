package no.nav.helse.spre.subsumsjon

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

class MappingDao(
    private val dataSource: DataSource
) {
    fun lagre(
        hendelseId: UUID,
        dokumentId: UUID,
        dokumentIdType: DokumentIdType,
        hendelseNavn: String,
        produsert: LocalDateTime
    ) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "INSERT INTO hendelse_dokument_mapping (hendelse_id, dokument_id, dokument_id_type, hendelse_type, publisert) VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING"
        session.run(
            queryOf(query, hendelseId, dokumentId, dokumentIdType.name, hendelseNavn, produsert).asUpdate
        )
    }

    fun hentSykmeldingId(hendelseId: UUID) = hentHendelseDokumentId(hendelseId, DokumentIdType.Sykmelding)
    fun hentSøknadId(hendelseId: UUID) = hentHendelseDokumentId(hendelseId, DokumentIdType.Søknad)
    fun hentInntektsmeldingId(hendelseId: UUID) = hentHendelseDokumentId(hendelseId, DokumentIdType.Inntektsmelding)

    private fun hentHendelseDokumentId(hendelseId: UUID, dokumentIdType: DokumentIdType): UUID? = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT dokument_id FROM hendelse_dokument_mapping WHERE hendelse_id = ? AND dokument_id_type = ?"
        session.run(
            queryOf(query, hendelseId, dokumentIdType.name).map { UUID.fromString(it.string("dokument_id")) }.asSingle
        )
    }

}

internal fun JsonNode.toUUID() = UUID.fromString(this.asText())
internal fun JsonNode.toUUIDs() = this.map { it.toUUID() }