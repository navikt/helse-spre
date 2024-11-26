package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetData.Companion.fromJson
import org.intellij.lang.annotations.Language
import java.time.*
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.jvm.internal.impl.descriptors.Visibilities.Local

class VedtakFattetDao(private val dataSource: DataSource) {

    companion object {
        val NITTEN_ÅTTI = LocalDateTime.of(1980, 1, 1, 0, 0, 0).toInstant(ZoneOffset.of("+00:00:00"))
        fun fromJson(originalMessage: String): VedtakFattetData {
            val jsonNode: JsonNode = objectMapper.readTree(originalMessage)
            return fromJson(jsonNode)
        }
    }

    fun lagre(vedtakFattetData: VedtakFattetData, json: String): Int {
        val id = vedtakFattetData.id
        val utbetalingId = vedtakFattetData.utbetalingId
        val fødselsnummer = vedtakFattetData.fødselsnummer

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

    fun journalført(vedtakFattetId: UUID) {
        @Language("PostgreSQL")
        val query = "UPDATE vedtak_fattet SET journalfort = now() WHERE id = ?"
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtakFattetId).asUpdate)
        }
    }

    fun erJournalført(vedtakFattetData: VedtakFattetData): Boolean {
        @Language("PostgreSQL")
        val query = "SELECT journalfort FROM vedtak_fattet WHERE id = ?"
        val journalførtTidspunkt: Instant? = sessionOf(dataSource).use { session ->
            session.run(queryOf(query, vedtakFattetData.id).map { row -> row.instantOrNull("journalfort") }.asSingle)
        }
        return journalførtTidspunkt != null && journalførtTidspunkt > NITTEN_ÅTTI;
    }

    internal fun finnVedtakFattetData(utbetalingId: UUID): List<VedtakFattetData> = finnJsonHvisFinnes(utbetalingId).let { vedtakFattetJson ->
        vedtakFattetJson.map { fromJson(it) }
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

}
