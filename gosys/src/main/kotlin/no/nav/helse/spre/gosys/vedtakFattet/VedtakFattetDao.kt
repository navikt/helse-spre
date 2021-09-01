package no.nav.helse.spre.gosys.vedtakFattet

import com.fasterxml.jackson.databind.JsonNode
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.gosys.objectMapper
import no.nav.helse.spre.gosys.vedtakFattet.VedtakFattetData.Companion.fromJson
import org.intellij.lang.annotations.Language
import java.util.*
import javax.sql.DataSource

class VedtakFattetDao(private val dataSource: DataSource) {

    companion object {
        fun fromJson(originalMessage: String): VedtakFattetData {
            val jsonNode: JsonNode = objectMapper.readTree(originalMessage)
            return fromJson(jsonNode)
        }
    }

    fun lagre(vedtakFattetData: VedtakFattetData, json: String) {
        val id = vedtakFattetData.id
        val utbetalingId = vedtakFattetData.utbetalingId
        val fødselsnummer = vedtakFattetData.fødselsnummer

        @Language("PostgreSQL")
        val query = "INSERT INTO vedtak_fattet (id, utbetaling_id, fnr, data) values(?, ?, ?, to_json(?::json)) ON CONFLICT DO NOTHING"
        sessionOf(dataSource).use { session ->
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

    internal fun finnVedtakFattetData(utbetalingId: UUID): List<VedtakFattetData> = finnJsonHvisFinnes(utbetalingId).let {
        it.map { fromJson(it) }
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
