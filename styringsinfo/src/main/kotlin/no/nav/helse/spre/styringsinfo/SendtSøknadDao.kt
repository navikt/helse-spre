package no.nav.helse.spre.styringsinfo

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.lang.IllegalStateException
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

    fun patchMelding(patchLevelPreCondition: Int?, sendtSøknad: SendtSøknad) {
        val preConditionClauseValue = if (patchLevelPreCondition == null) "IS NULL" else "= :patchLevelPreCondition"
        @Language("PostgreSQL")
        val query = """
            UPDATE sendt_soknad
            SET patch_level = :patchLevel, 
                melding = CAST(:melding as json)
            WHERE hendelse_id = :hendelseId 
            AND patch_level ${preConditionClauseValue} 
            """
        sessionOf(dataSource).use { session ->
            val rowsChanged = session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to sendtSøknad.hendelseId,
                        "melding" to sendtSøknad.melding,
                        "patchLevel" to sendtSøknad.patchLevel,
                        "patchLevelPreCondition" to patchLevelPreCondition
                    )
                ).asUpdate
            )
            if (rowsChanged != 1) {
                throw IllegalStateException("Uventet antall rader å patch'e (${rowsChanged}) med hendelseId=${sendtSøknad.hendelseId} med patchLevelPreCondition=$patchLevelPreCondition")
            }
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

