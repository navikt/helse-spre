package no.nav.helse.spre.styringsinfo

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class SendtSøknadDao(private val datasource: DataSource) {

    fun lagre(sendtSøknad: SendtSøknad) {
        @Language("PostgreSQL")
        val query = """INSERT INTO sendt_soknad (sendt, korrigerer, fom, tom, hendelse_id, melding)
            VALUES (:sendt, :korrigerer, :fom, :tom, :hendelse_id,CAST(:melding as json))
            ON CONFLICT DO NOTHING;""".trimIndent()

        sessionOf(datasource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "sendt" to sendtSøknad.sendt.toOsloOffset(),
                        "korrigerer" to sendtSøknad.korrigerer,
                        "fom" to sendtSøknad.fom,
                        "tom" to sendtSøknad.tom,
                        "hendelse_id" to sendtSøknad.hendelseId,
                        "melding" to sendtSøknad.melding
                    )
                ).asUpdate
            )
        }
    }

    fun tellSøknader(): Int {
        @Language("PostgreSQL")
        val query = "SELECT count(*) FROM sendt_soknad"

        return sessionOf(datasource).use { session ->
            session.run(queryOf(query).map { row -> row.int(1) }.asSingle)
        } ?: 0
    }

    fun tellSøknaderSomFeiler(): Int {
        @Language("PostgreSQL")
        val query = "SELECT count(*) FROM sendt_soknad"

        return sessionOf(datasource).use { session ->
            session.run(
                queryOf(query).asUpdate
            )
        }
    }
}

