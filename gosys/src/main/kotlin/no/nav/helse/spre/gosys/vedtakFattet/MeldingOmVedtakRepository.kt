package no.nav.helse.spre.gosys.vedtakFattet

import java.util.*
import kotliquery.TransactionalSession
import kotliquery.queryOf

class MeldingOmVedtakRepository {

    context(session: TransactionalSession)
    fun lagre(meldingOmVedtak: MeldingOmVedtak) {
        session.run(
            queryOf(
                // language=postgresql
                "INSERT INTO vedtak_fattet (id, utbetaling_id, fnr, data, journalfort)" +
                    " values (:id, :utbetaling_id, :fnr, to_json(:json::json), :journalfort)" +
                    " ON CONFLICT(id) DO UPDATE SET journalfort = excluded.journalfort",
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

    context(session: TransactionalSession)
    internal fun finn(utbetalingId: UUID): MeldingOmVedtak? =
        session.run(
            queryOf(
                // language=postgresql
                "SELECT id, fnr, journalfort, data FROM vedtak_fattet WHERE utbetaling_id = ?",
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
