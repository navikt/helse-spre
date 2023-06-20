package no.nav.helse.spre.styringsinfo

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class SendtSøknadDao(private val datasource: DataSource) {

    fun lagre(sendtSøknad: SendtSøknad) {
        @Language("PostgreSQL")
        val query = """INSERT INTO sendt_soknad (sendt, korrigerer, fnr, fom, tom, hendelse_id, melding) 
            VALUES (?,?,?,?,?,?,CAST(? as json)) 
            ON CONFLICT DO NOTHING;""".trimIndent()

        sessionOf(datasource).use { session ->
            session.run(queryOf(
                query,
                sendtSøknad.sendt,
                sendtSøknad.korrigerer,
                sendtSøknad.fnr,
                sendtSøknad.fom,
                sendtSøknad.tom,
                sendtSøknad.hendelseId,
                sendtSøknad.melding
            ).asUpdate)
        }
    }
}