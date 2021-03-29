package no.nav.helse.spre.gosys

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

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
                    log.info("Oppdaget duplikat, oppretter ikke journalpost for $id")
                }
            }
        }
    }
}