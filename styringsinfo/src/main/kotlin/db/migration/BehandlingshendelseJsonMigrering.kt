package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import org.slf4j.LoggerFactory
import java.sql.ResultSet
import java.sql.Statement
import java.time.Duration
import java.time.LocalDateTime

internal abstract class BehandlingshendelseJsonMigrering: BaseJavaMigration() {
    private val logg = LoggerFactory.getLogger(this::class.java)

    override fun migrate(context: Context) {
        val versjon = nyVersjon()?.let { "'$it'" } ?: BEHOLD_VERSJON
        val startet = LocalDateTime.now()
        var korrigerteRaderTotalt = 0L

        context.connection.createStatement().use { korrigeringsStatement ->
            context.connection.createStatement().use { queryStatement ->
                queryStatement.executeQuery(query()).use { resultSet ->
                    do {
                        val korrigerteRaderIBatch = håndterEnBatch(resultSet, korrigeringsStatement, versjon)
                        korrigerteRaderTotalt += korrigerteRaderIBatch
                        if (korrigerteRaderIBatch == batchSize) logg.info("[In progress] Har korrigert $korrigerteRaderTotalt rader.")
                    } while (korrigerteRaderIBatch != 0)
                }
            }
        }

        val tidsbruk = Duration.between(startet, LocalDateTime.now())
        logg.info("[Finished] Korrigerte totalt $korrigerteRaderTotalt rader på $tidsbruk (${tidsbruk.seconds} sekunder)")
    }

    private fun håndterEnBatch(resultSet: ResultSet, statement: Statement, versjon: String): Int {
        val gamleSekvensnummer = mutableListOf<Long>()

        while (gamleSekvensnummer.size < batchSize && resultSet.next()) {
            val (gammeltSekvensnummer, gammelData, erKorrigert) = resultSet.gammelRad()
            if (erKorrigert) continue // I tilfelle noen ikke har AND er_korrigert=false i query

            val nyData = nyData(gammelData)

            val nyRad = """
                insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert)
                select sakId, behandlingId, funksjonellTid, $versjon, '${nyData}'::jsonb, siste, hendelseId, false 
                from behandlingshendelse 
                where sekvensnummer=${gammeltSekvensnummer};
            """

            gamleSekvensnummer.add(gammeltSekvensnummer)
            statement.addBatch(nyRad)
        }
        if (gamleSekvensnummer.size > 0) {
            val gamleRader = """
                update behandlingshendelse set siste=false, er_korrigert=true where sekvensnummer in ${gamleSekvensnummer.joinToString(prefix = "(", postfix = ")")};
            """
            statement.addBatch(gamleRader)
            statement.executeBatch()
            statement.clearBatch()
        }
        return gamleSekvensnummer.size
    }

    /** Query som identifiserer radene som skal korrigeres. Må være mulig å hente ut "sekvensnummer", "data" og "er_korrigert" */
    abstract fun query(): String
    /** Settes om de nye radene skal få en annen versjon enn raden som korrigeres */
    abstract fun nyVersjon(): Versjon?
    /** Får data fra raden som skal korrigeres og returnerer data som skal inn i den nye raden */
    abstract fun nyData(gammelData: ObjectNode): ObjectNode
    /** Antall rader som skal korrigeres per batch **/
    open val batchSize: Int = 25_000

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private const val BEHOLD_VERSJON = "versjon"
        private fun ResultSet.gammelRad() = Triple(
            getLong("sekvensnummer"),
            objectMapper.readTree(getString("data")) as ObjectNode,
            getBoolean("er_korrigert")
        )
    }
}