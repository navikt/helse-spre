package no.nav.helse.spre.gosys.vedtakFattet

import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class MeldingOmVedtakRepository(private val dataSource: DataSource) {

    fun lagre(meldingOmVedtak: MeldingOmVedtak) {
        @Language("PostgreSQL")
        val query = "INSERT INTO vedtak_fattet (id, utbetaling_id, fnr, data, journalfort)" +
            " values (:id, :utbetaling_id, :fnr, to_json(:json::json), :journalfort)" +
            " ON CONFLICT(id) DO UPDATE SET journalfort = excluded.journalfort"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    mapOf(
                        "id" to meldingOmVedtak.id,
                        "utbetaling_id" to meldingOmVedtak.utbetalingId,
                        "fnr" to meldingOmVedtak.fødselsnummer,
                        "json" to meldingOmVedtak.json,
                        "journalfort" to meldingOmVedtak.journalførtTidspunkt
                    )
                ).asUpdate
            )
        }
    }

    internal fun finn(utbetalingId: UUID): MeldingOmVedtak? =
        sessionOf(dataSource, strict = true).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT id, fnr, journalfort, data FROM vedtak_fattet WHERE utbetaling_id = ?"
            return session.run(
                queryOf(
                    query,
                    utbetalingId,
                ).map {
                    MeldingOmVedtak(
                        id = UUID.fromString(it.string("id")),
                        utbetalingId = utbetalingId,
                        journalførtTidspunkt = it.instantOrNull("journalfort"),
                        fødselsnummer = it.string("fnr"),
                        json = it.string("data"),
                    )
                }.asSingle
            )
        }
}
