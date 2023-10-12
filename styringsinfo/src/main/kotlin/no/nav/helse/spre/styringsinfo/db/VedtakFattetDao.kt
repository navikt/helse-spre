package no.nav.helse.spre.styringsinfo.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.domain.VedtakFattet
import no.nav.helse.spre.styringsinfo.toOsloOffset
import no.nav.helse.spre.styringsinfo.toOsloTid
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

interface VedtakFattetDaoInterface {

    fun lagre(vedtakFattet: VedtakFattet)
    fun oppdaterMelding(vedtakFattet: VedtakFattet): Int
    fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int = 1000): List<VedtakFattet>
}

class VedtakFattetDao(private val dataSource: DataSource): VedtakFattetDaoInterface {

    override fun lagre(vedtakFattet: VedtakFattet) =
        sessionOf(dataSource).use { session ->
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

    override fun oppdaterMelding(vedtakFattet: VedtakFattet): Int {
        @Language("PostgreSQL")
        val query = """
            UPDATE vedtak_fattet
            SET patch_level = :patchLevel, 
                melding = CAST(:melding as json)
            WHERE hendelse_id = :hendelseId
            """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "hendelseId" to vedtakFattet.hendelseId,
                        "melding" to vedtakFattet.melding,
                        "patchLevel" to vedtakFattet.patchLevel
                    )
                ).asUpdate
            )
        }
    }

    override fun hentMeldingerMedPatchLevelMindreEnn(patchLevel: Int, antallMeldinger: Int): List<VedtakFattet> {
        @Language("PostgreSQL")
        val query = """
            SELECT fom, tom, vedtak_fattet_tidspunkt, hendelse_id, melding, patch_level
            FROM vedtak_fattet 
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
                    VedtakFattet(
                        fom = row.localDate("fom"),
                        tom = row.localDate("tom"),
                        vedtakFattetTidspunkt = row.zonedDateTime("vedtak_fattet_tidspunkt").toOsloTid(),
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