package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.gosys.objectMapper
import org.intellij.lang.annotations.Language

class VedtakFattetDao(private val dataSource: DataSource) {

    companion object {
        val NITTEN_ÅTTI = LocalDateTime.of(1980, 1, 1, 0, 0, 0).toInstant(ZoneOffset.of("+00:00:00"))
    }

    fun lagre(
        id: UUID,
        utbetalingId: UUID?,
        fødselsnummer: String,
        json: String
    ): Int {
        @Language("PostgreSQL")
        val query = "INSERT INTO vedtak_fattet (id, utbetaling_id, fnr, data) values(?, ?, ?, to_json(?::json)) ON CONFLICT DO NOTHING"
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    id,
                    utbetalingId,
                    fødselsnummer,
                    json
                ).asUpdate
            )
        }
    }

    fun lagre(vedtakFattetRad: VedtakFattetRad) {
        @Language("PostgreSQL")
        val query = "INSERT INTO vedtak_fattet (id, utbetaling_id, fnr, data) values(?, ?, ?, to_json(?::json)) ON CONFLICT DO NOTHING"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    vedtakFattetRad.id,
                    vedtakFattetRad.utbetalingId,
                    vedtakFattetRad.fødselsnummer,
                    vedtakFattetRad.data
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
                        journalført = it.instantOrNull("journalfort"),
                        fødselsnummer = it.string("fnr"),
                        data = it.string("data"),
                    )
                }.asSingle
            )
        }

    fun journalfør(vedtakFattetId: UUID) {
        @Language("PostgreSQL")
        val query = "UPDATE vedtak_fattet SET journalfort = now() WHERE id = ?"
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtakFattetId).asUpdate)
        }
    }

    fun erJournalført(vedtakFattetId: UUID): Boolean {
        @Language("PostgreSQL")
        val query = "SELECT journalfort FROM vedtak_fattet WHERE id = ?"
        val journalførtTidspunkt: Instant? = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtakFattetId).map { row -> row.instantOrNull("journalfort") }.asSingle)
        }
        return journalførtTidspunkt != null && journalførtTidspunkt > NITTEN_ÅTTI;
    }

    data class VedtakFattetIder(
        val id: UUID,
        val vedtaksperiodeId: UUID,
    )

    data class VedtakFattetRad(
        val id: UUID,
        val utbetalingId: UUID?,
        val fødselsnummer: String,
        val journalført: Instant?,
        val data: String,
    )

    internal fun finnVedtakFattetData(utbetalingId: UUID): VedtakFattetIder? =
        finnJsonHvisFinnes(utbetalingId).singleOrNullOrThrow()?.let {
            val jsonNode: JsonNode = objectMapper.readTree(it)
            VedtakFattetIder(
                id = UUID.fromString(jsonNode["@id"].asText()),
                vedtaksperiodeId = UUID.fromString(jsonNode["vedtaksperiodeId"].asText()),
            )
        }

    private fun finnJsonHvisFinnes(utbetalingId: UUID): List<String> =
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT data FROM vedtak_fattet WHERE utbetaling_id = ?"
            return session.run(
                queryOf(
                    query,
                    utbetalingId,
                ).map { it.string("data") }.asList
            )
        }

    private fun <R> Collection<R>.singleOrNullOrThrow() =
        if (size < 2) this.firstOrNull()
        else throw IllegalStateException("Listen inneholder mer enn ett element!")
}
