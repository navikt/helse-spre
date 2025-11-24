package no.nav.helse.spre.gosys.vedtakFattet

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import org.intellij.lang.annotations.Language

class VedtakFattetDao(private val dataSource: DataSource) {

    fun lagre(vedtakFattetRad: VedtakFattetRad) {
        @Language("PostgreSQL")
        val query = "INSERT INTO vedtak_fattet (id, utbetaling_id, fnr, data, journalfort) values(?, ?, ?, to_json(?::json), ?) ON CONFLICT(id) DO UPDATE SET journalfort = excluded.journalfort"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    vedtakFattetRad.id,
                    vedtakFattetRad.utbetalingId,
                    vedtakFattetRad.fødselsnummer,
                    vedtakFattetRad.data,
                    vedtakFattetRad.journalførtTidspunkt
                ).asUpdate
            )
        }
    }

    internal fun finn(utbetalingId: UUID): VedtakFattetRad? =
        sessionOf(dataSource, strict = true).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT id, fnr, journalfort, data FROM vedtak_fattet WHERE utbetaling_id = ?"
            return session.run(
                queryOf(
                    query,
                    utbetalingId,
                ).map {
                    VedtakFattetRad(
                        id = UUID.fromString(it.string("id")),
                        utbetalingId = utbetalingId,
                        journalførtTidspunkt = it.instantOrNull("journalfort"),
                        fødselsnummer = it.string("fnr"),
                        data = it.string("data"),
                    )
                }.asSingle
            )
        }

    class VedtakFattetRad(
        val id: UUID,
        val utbetalingId: UUID?,
        val fødselsnummer: String,
        journalførtTidspunkt: Instant?,
        val data: String,
    ) {
        var journalførtTidspunkt: Instant? = journalførtTidspunkt
            private set

        fun erJournalført(): Boolean {
            val journalførtTidspunkt = this.journalførtTidspunkt
            return journalførtTidspunkt != null && journalførtTidspunkt > NITTEN_ÅTTI
        }

        fun journalfør() {
            this.journalførtTidspunkt = Instant.now()
        }

        private companion object {
            val NITTEN_ÅTTI = LocalDateTime.of(1980, 1, 1, 0, 0, 0).toInstant(ZoneOffset.of("+00:00:00"))
        }
    }
}
