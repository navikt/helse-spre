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
    fun lagre(hendelseId: UUID, dokumentId: UUID, hendelseNavn: String, produsert: LocalDateTime) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "INSERT INTO hendelse_dokument_kobling (hendelse_id, dokument_id, hendelse_navn, publisert) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING"
        session.run(
            queryOf(query, hendelseId, dokumentId, hendelseNavn, produsert).asUpdate
        )
    }

    fun hent(hendelseId: UUID): UUID? = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT dokument_id FROM hendelse_dokument_kobling WHERE hendelse_id = ?"
        session.run(
            queryOf(query, hendelseId).map { UUID.fromString(it.string("dokument_id")) }.asSingle
        )
    }
}

internal fun JsonNode.toUUID() = UUID.fromString(this.asText())
internal fun JsonNode.toUUIDs() = this.map { it.toUUID() }