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
            VALUES (:fnr,:fom,:tom,:vedtak_fattet_tidspunkt, :hendelse_id,CAST(:melding as json)) 
            ON CONFLICT DO NOTHING;""".trimIndent()

        sessionOf(datasource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "fnr" to vedtakFattet.fnr,
                        "fom" to vedtakFattet.fom,
                        "tom" to vedtakFattet.tom,
                        "vedtak_fattet_tidspunkt" to vedtakFattet.vedtakFattetTidspunkt,
                        "hendelse_id" to vedtakFattet.hendelseId,
                        "melding" to vedtakFattet.melding
                    )
                ).asUpdate
            )
        }
    }
}