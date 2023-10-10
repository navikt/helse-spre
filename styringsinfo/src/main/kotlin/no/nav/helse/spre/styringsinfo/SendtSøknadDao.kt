package no.nav.helse.spre.styringsinfo

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import javax.sql.DataSource

class SendtSøknadDao(private val dataSource: DataSource) {

    fun lagre(sendtSøknad: SendtSøknad) {
        @Language("PostgreSQL")
        val query = """
            INSERT INTO sendt_soknad (sendt, korrigerer, fom, tom, hendelse_id, melding, patch_level)
            VALUES (:sendt, :korrigerer, :fom, :tom, :hendelse_id,CAST(:melding as json), :patchLevel)
            ON CONFLICT DO NOTHING;
            """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "sendt" to sendtSøknad.sendt.toOsloOffset(),
                        "korrigerer" to sendtSøknad.korrigerer,
                        "fom" to sendtSøknad.fom,
                        "tom" to sendtSøknad.tom,
                        "hendelse_id" to sendtSøknad.hendelseId,
                        "melding" to sendtSøknad.melding,
                        "patchLevel" to sendtSøknad.patchLevel
                    )
                ).asUpdate
            )
        }
    }

    fun oppdaterMelding(sendtSøknad: SendtSøknad) {
        @Language("PostgreSQL")
        val query = """
            UPDATE sendt_soknad
            SET patch_level = :patchLevel, 
                melding = CAST(:melding as json)
            WHERE hendelse_id = :hendelseId
            """
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to sendtSøknad.hendelseId,
                        "melding" to sendtSøknad.melding,
                        "patchLevel" to sendtSøknad.patchLevel
                    )
                ).asUpdate
            )
        }
    }

    fun tellSøknader(): Int? {
        @Language("PostgreSQL")
        val query = "SELECT count(*) AS antall FROM sendt_soknad"

        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(query).map { row ->
                    row.int("antall")
                }.asSingle
            )
        }
    }
}

