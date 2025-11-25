package no.nav.helse.spre.gosys

import java.util.*
import javax.sql.DataSource
import kotliquery.TransactionalSession
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

    context(session: TransactionalSession)
    fun insertTilDuplikatsjekk(id: UUID) {
        session.run(
            queryOf(
                // language=postgresql
                "INSERT INTO duplikatsjekk (id) VALUES (?)",
                id
            ).asUpdate
        )
    }

    context(session: TransactionalSession)
    fun erDuplikat(id: UUID): Boolean =
        session.run(
            queryOf(
                // language=postgresql
                statement = "SELECT 1 FROM duplikatsjekk WHERE id = :id",
                paramMap = mapOf("id" to id),
            ).map { true }.asSingle
        ) ?: false
}
