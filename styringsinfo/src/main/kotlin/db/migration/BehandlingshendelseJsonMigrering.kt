package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.ResultSet

internal abstract class BehandlingshendelseJsonMigrering: BaseJavaMigration() {

    override fun migrate(context: Context) {
        val queryStatement = context.connection.createStatement()
        val batchStatement = context.connection.createStatement()

        val versjon = nyVersjon()?.let { "'$it'" } ?: BEHOLD_VERSJON

        queryStatement.use { statement -> statement.executeQuery(query()).use { resultSet ->
            while (resultSet.next()) {
                val (gammeltSekvensnummer, gammelData, erKorrigert) = resultSet.gammelRad()
                if (erKorrigert) continue // I tilfelle noen ikke har AND er_korrigert=false i query

                val nyData = nyData(gammelData)

                val insert = """
                    insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert)
                    select sakId, behandlingId, funksjonellTid, $versjon, '${nyData}'::jsonb, siste, hendelseId, false 
                    from behandlingshendelse 
                    where sekvensnummer=${gammeltSekvensnummer};
                """

                val update = """
                    update behandlingshendelse set siste=false, er_korrigert=true where sekvensnummer=${gammeltSekvensnummer};
                """

                batchStatement.addBatch(insert)
                batchStatement.addBatch(update)
            }
        }}
        batchStatement.use { statement -> statement.executeBatch() }
    }
    // Query som identifiserer radene som skal korrigeres. Må være mulig å hente ut "sekvensnummer", "data" og "er_korrigert"
    abstract fun query(): String
    // Settes om de nye radene skal få en annen versjon enn raden som korrigeres
    abstract fun nyVersjon(): Versjon?
    // Får data fra raden som skal korrigeres og returnerer data som skal inn i den nye raden
    abstract fun nyData(gammelData: ObjectNode): ObjectNode

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