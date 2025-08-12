package no.nav.helse.spre.gosys

import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class DuplikatsjekkDao(private val datasource: DataSource) {

    fun sjekkDuplikat(id: UUID, callback: () -> Unit) {
        sessionOf(datasource).use {
            @Language("PostgreSQL")
            val query = "INSERT INTO duplikatsjekk (id) VALUES (?) ON CONFLICT DO NOTHING;"
            it.transaction { transaction ->
                val recordsChanged = transaction.run(queryOf(query, id).asUpdate)
                if (recordsChanged > 0) {
                    callback()
                } else {
                    logg.info("Oppdaget duplikat, oppretter ikke journalpost for $id")
                }
            }
        }
    }

    fun insertTilDuplikatsjekk(id: UUID) {
        sessionOf(datasource).use {
            it.transaction { transaction ->
                transaction.run(
                    queryOf(
                        // language=postgresql
                        "INSERT INTO duplikatsjekk (id) VALUES (?)",
                        id
                    ).asUpdate
                )
            }
        }
    }

    fun erDuplikat(id: UUID): Boolean =
        sessionOf(datasource).use { session ->
            session.run(
                queryOf(
                    // language=postgresql
                    statement = "SELECT 1 FROM duplikatsjekk WHERE id = :id",
                    paramMap = mapOf("id" to id),
                ).map { true }.asSingle
            ) ?: false
        }
}
