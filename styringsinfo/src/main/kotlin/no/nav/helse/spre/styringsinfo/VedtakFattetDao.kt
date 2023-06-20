package no.nav.helse.spre.styringsinfo

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class VedtakFattetDao(private val datasource: DataSource) {

    fun lagre(vedtakFattet: VedtakFattet) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtak_fattet (fnr, fom, tom, vedtak_fattet_tidspunkt, hendelse_id, melding) 
            VALUES (?,?,?,?,?,CAST(? as json)) 
            ON CONFLICT DO NOTHING;""".trimIndent()

        sessionOf(datasource).use { session ->
            session.run(queryOf(
                query,
                vedtakFattet.fnr,
                vedtakFattet.fom,
                vedtakFattet.tom,
                vedtakFattet.vedtakFattetTidspunkt,
                vedtakFattet.hendelseId,
                vedtakFattet.melding
            ).asUpdate)
        }
    }
}