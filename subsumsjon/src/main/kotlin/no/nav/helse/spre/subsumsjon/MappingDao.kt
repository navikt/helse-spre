package no.nav.helse.spre.subsumsjon

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
        val query = "INSERT INTO hendelse_id_mapping (hendelse_id, dokument_id, hendelse_navn, publisert) VALUES (?, ?, ?, ?)"
        session.run(
            queryOf(query, hendelseId, dokumentId, hendelseNavn, produsert).asUpdate
        )
    }

    fun hent(hendelseId: UUID): UUID? = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT dokument_id FROM hendelse_id_mapping WHERE hendelse_id = ?"
        session.run(
            queryOf(query, hendelseId).map { UUID.fromString(it.string("dokument_id")) }.asSingle
        )
    }
}