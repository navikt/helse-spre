package no.nav.helse.spre.styringsinfo

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class VedtakForkastetDao(private val datasource: DataSource) {

    fun lagre(vedtakForkastet: VedtakForkastet) =
        sessionOf(datasource).use { session ->
            session.transaction { tx ->
                lagreVedtaket(vedtakForkastet, tx)
                if (vedtakForkastet.hendelser.isEmpty()) return
                lagreDokumentreferansene(vedtakForkastet.hendelseId, vedtakForkastet.hendelser, tx)
            }
        }

    private fun lagreVedtaket(vedtakForkastet: VedtakForkastet, tx: TransactionalSession) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtak_forkastet (fnr, fom, tom, forkastet_tidspunkt, hendelse_id, melding) 
            VALUES (:fnr,:fom,:tom,:forkastet_tidspunkt,:hendelse_id,CAST(:melding as json)) 
            ON CONFLICT DO NOTHING;""".trimIndent()

        tx.run(
            queryOf(
                query,
                mapOf(
                    "fnr" to vedtakForkastet.fnr,
                    "fom" to vedtakForkastet.fom,
                    "tom" to vedtakForkastet.tom,
                    "forkastet_tidspunkt" to vedtakForkastet.forkastetTidspunkt,
                    "hendelse_id" to vedtakForkastet.hendelseId,
                    "melding" to vedtakForkastet.melding
                )
            ).asUpdate
        )
    }

    private fun lagreDokumentreferansene(vedtak: UUID, hendelser: List<UUID>, tx: TransactionalSession) {
        @Language("PostgreSQL")
        val q = """
            insert into vedtak_dokument_mapping(vedtak_hendelse_id, dokument_hendelse_id) values(:vedtak, :dokument)
        """.trimIndent()
        hendelser.forEach { hendelse ->
            tx.run(queryOf(q, mapOf("vedtak" to vedtak, "dokument" to hendelse)).asUpdate)
        }
    }
}