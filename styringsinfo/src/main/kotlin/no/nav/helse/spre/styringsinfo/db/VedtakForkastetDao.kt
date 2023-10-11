package no.nav.helse.spre.styringsinfo.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.domain.VedtakForkastet
import no.nav.helse.spre.styringsinfo.toOsloOffset
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
        val query = """INSERT INTO vedtak_forkastet (fom, tom, forkastet_tidspunkt, hendelse_id, melding, patch_level)
            VALUES (:fom,:tom,:forkastet_tidspunkt,:hendelse_id,CAST(:melding as json), :patchLevel)
            ON CONFLICT DO NOTHING;""".trimIndent()

        tx.run(
            queryOf(
                query,
                mapOf(
                    "fom" to vedtakForkastet.fom,
                    "tom" to vedtakForkastet.tom,
                    "forkastet_tidspunkt" to vedtakForkastet.forkastetTidspunkt.toOsloOffset(),
                    "hendelse_id" to vedtakForkastet.hendelseId,
                    "melding" to vedtakForkastet.melding,
                    "patchLevel" to vedtakForkastet.patchLevel
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