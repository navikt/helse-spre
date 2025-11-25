package no.nav.helse.spre.gosys.utbetaling

import com.fasterxml.jackson.databind.JsonNode
import java.util.*
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.helse.spre.gosys.objectMapper

class UtbetalingDao {
    companion object {
        fun fromJson(originalMessage: String): Utbetaling {
            val jsonNode: JsonNode = objectMapper.readTree(originalMessage)
            return Utbetaling.fromJson(jsonNode)
        }
    }

    context(session: TransactionalSession)
    fun lagre(id: UUID, event: String, utbetalingData: Utbetaling, json: String) {
        session.run(
            queryOf(
                // language=postgresql
                "INSERT INTO utbetaling(id, utbetaling_id, event, fnr, data)" +
                    " values(?, ?, ?, ?, to_json(?::json))" +
                    " ON CONFLICT DO NOTHING",
                id,
                utbetalingData.utbetalingId,
                event,
                utbetalingData.fødselsnummer,
                json,
            ).asUpdate
        )
    }

    context(session: TransactionalSession)
    internal fun finnUtbetalingData(utbetalingId: UUID): Utbetaling? = finnJsonHvisFinnes(utbetalingId)?.let { fromJson(it) }

    context(session: TransactionalSession)
    private fun finnJsonHvisFinnes(utbetalingId: UUID): String? =
        session.run(
            queryOf(
                // language=postgresql
                "SELECT data FROM utbetaling WHERE utbetaling_id = ?",
                utbetalingId,
            ).map { it.string("data") }.asSingle
        )
}




