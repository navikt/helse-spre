package no.nav.helse.spre.styringsinfo.datafortelling.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.datafortelling.domain.VedtakForkastet
import no.nav.helse.spre.styringsinfo.toOsloOffset
import no.nav.helse.spre.styringsinfo.toOsloTid
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource


interface VedtakForkastetDaoInterface {

    fun lagre(vedtakForkastet: VedtakForkastet)
    fun oppdaterMelding(vedtakForkastet: VedtakForkastet): Int
    fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int = 1000): List<VedtakForkastet>
}

class VedtakForkastetDao(private val dataSource: DataSource) : VedtakForkastetDaoInterface {

    override fun lagre(vedtakForkastet: VedtakForkastet) =
        sessionOf(dataSource).use { session ->
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

    override fun oppdaterMelding(vedtakForkastet: VedtakForkastet): Int {
        @Language("PostgreSQL")
        val query = """
            UPDATE vedtak_forkastet
            SET patch_level = :patchLevel, 
                melding = CAST(:melding as json)
            WHERE hendelse_id = :hendelseId
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to vedtakForkastet.hendelseId,
                        "melding" to vedtakForkastet.melding,
                        "patchLevel" to vedtakForkastet.patchLevel
                    )
                ).asUpdate
            )
        }
    }

    override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<VedtakForkastet> {
        @Language("PostgreSQL")
        val query = """
            SELECT fom, tom, forkastet_tidspunkt, hendelse_id, melding, patch_level
            FROM vedtak_forkastet
            WHERE patch_level < :patchLevel
            LIMIT :antallMeldinger
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "patchLevel" to patchLevel,
                        "antallMeldinger" to antallMeldinger
                    )
                ).map { row ->
                    VedtakForkastet(
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        forkastetTidspunkt = row.zonedDateTime("forkastet_tidspunkt").toOsloTid(),
                        hendelseId = row.uuid("hendelse_id"),
                        melding = row.string("melding"),
                        patchLevel = row.int("patch_level"),
                        hendelser = emptyList()
                    )
                }.asList
            )
        }
    }
}