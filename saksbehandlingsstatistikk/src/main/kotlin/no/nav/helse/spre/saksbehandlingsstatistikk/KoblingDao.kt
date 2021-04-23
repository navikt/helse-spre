package no.nav.helse.spre.saksbehandlingsstatistikk

import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class KoblingDao(private val dataSource: DataSource) {

    fun opprettSøknadKobling(søknadId: UUID, utbetalingId: UUID?, vedtaksperiodeId: UUID?) {
        @Language("PostgreSQL")
        val query = "INSERT INTO soknad_kobling(soknad_id, utbetaling_id, vedtaksperiode_id) VALUES(?,?,?) ON CONFLICT DO NOTHING"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    søknadId,
                    utbetalingId,
                    vedtaksperiodeId
                ).asUpdate
            )
        }
    }

    fun finnSøknadIdForUtbetalingId(utbetalingId: UUID) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM soknad_kobling WHERE utbetaling_id = ?::uuid"
        session.run(
            queryOf(query, utbetalingId.toString())
                .map { row -> row.let { UUID.fromString(row.string("soknad_id")) } }
                .asSingle
        )
    }

    fun finnSøknadIdForVedtaksperiodeId(vedtaksperiodeId: UUID) = sessionOf(dataSource).use { session ->
        @Language("PostgreSQL")
        val query = "SELECT * FROM soknad_kobling WHERE vedtaksperiode_id = ?::uuid"
        session.run(
            queryOf(query, vedtaksperiodeId.toString())
                .map { row -> row.let { UUID.fromString(row.string("soknad_id")) } }
                .asSingle
        )
    }
}