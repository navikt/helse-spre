package no.nav.helse.spre.styringsinfo.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.domain.VedtakFattet
import no.nav.helse.spre.styringsinfo.toOsloOffset
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class VedtakFattetDao(private val datasource: DataSource) {

    fun lagre(vedtakFattet: VedtakFattet) =
        sessionOf(datasource).use { session ->
            session.transaction { tx ->
                lagreVedtaket(vedtakFattet, tx)
                if (vedtakFattet.hendelser.isEmpty()) return
                lagreDokumentreferansene(vedtakFattet.hendelseId, vedtakFattet.hendelser, tx)
            }
        }

    private fun lagreVedtaket(vedtakFattet: VedtakFattet, tx: TransactionalSession) {
        @Language("PostgreSQL")
        val query = """INSERT INTO vedtak_fattet (fom, tom, vedtak_fattet_tidspunkt, hendelse_id, melding, patch_level)
            VALUES (:fom,:tom,:vedtak_fattet_tidspunkt, :hendelse_id,CAST(:melding as json), :patchLevel)
            ON CONFLICT DO NOTHING;""".trimIndent()

        tx.run(
            queryOf(
                query,
                mapOf(
                    "fom" to vedtakFattet.fom,
                    "tom" to vedtakFattet.tom,
                    "vedtak_fattet_tidspunkt" to vedtakFattet.vedtakFattetTidspunkt.toOsloOffset(),
                    "hendelse_id" to vedtakFattet.hendelseId,
                    "melding" to vedtakFattet.melding,
                    "patchLevel" to vedtakFattet.patchLevel
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