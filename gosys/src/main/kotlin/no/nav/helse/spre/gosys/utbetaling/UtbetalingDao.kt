package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import java.util.*
import javax.sql.DataSource
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.gosys.objectMapper
import org.intellij.lang.annotations.Language

class UtbetalingDao(private val dataSource: DataSource) {

    companion object {
        fun fromJson(originalMessage: String): Utbetaling {
            val jsonNode: JsonNode = objectMapper.readTree(originalMessage)
            return Utbetaling.fromJson(jsonNode)
        }
    }

    fun lagre(id: UUID, event: String, utbetalingData: Utbetaling, json: String) {
        val utbetalingId = utbetalingData.utbetalingId
        val fødselsnummer = utbetalingData.fødselsnummer

        @Language("PostgreSQL")
        val query = "INSERT INTO utbetaling(id, utbetaling_id, event, fnr, data) values(?, ?, ?, ?, to_json(?::json)) ON CONFLICT DO NOTHING"
        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(
                    query,
                    id,
                    utbetalingId,
                    event,
                    fødselsnummer,
                    json,
                ).asUpdate
            )
        }
    }

    internal fun finnUtbetalingData(utbetalingId: UUID): Utbetaling? = finnJsonHvisFinnes(utbetalingId)?.let { fromJson(it) }

    private fun finnJsonHvisFinnes(utbetalingId: UUID): String? {
        sessionOf(dataSource).use { session ->
            @Language("PostgreSQL")
            val query = "SELECT data FROM utbetaling WHERE utbetaling_id = ?"
            return session.run(
                queryOf(
                    query,
                    utbetalingId,
                ).map { it.string("data") }.asSingle
            )
        }
    }
}




