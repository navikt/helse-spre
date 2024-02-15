package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.isMissingOrNull
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.ResultSet

internal class V29__uppercase_enum_verdier: BaseJavaMigration() {
    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
    override fun migrate(context: Context) {
        val statement = context.connection.createStatement()
        val batchStatement = context.connection.createStatement()
        val query = """
            select * from behandlingshendelse where versjon='0.0.1' and er_korrigert=false; 
        """
        val nyVersjon = "0.0.2"
        statement.executeQuery(query).use {
            while (it.next()) {
                val interessanteFelter = it.toBehandlingshendelseRad()
                interessanteFelter.oppdaterFeltFor("behandlingstatus")
                interessanteFelter.oppdaterFeltFor("behandlingsmetode")
                interessanteFelter.oppdaterFeltFor("behandlingskilde")
                interessanteFelter.oppdaterFeltFor("behandlingsresultat")
                interessanteFelter.oppdaterFeltFor("behandlingtype")

                val insert = """
                    insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert)
                    select sakId, behandlingId, funksjonellTid, '$nyVersjon', '${interessanteFelter.data}'::jsonb, siste, hendelseId, false 
                    from behandlingshendelse 
                    where sekvensnummer=${interessanteFelter.sekvensnummer};
                """

                val update = """
                    update behandlingshendelse set siste=false, er_korrigert=true where sekvensnummer=${interessanteFelter.sekvensnummer};
                """

                batchStatement.addBatch(insert)
                batchStatement.addBatch(update)
            }
        }
        batchStatement.executeBatch()

        // TODOS:
        //  1. Fjern unntakshåndteringen i DAO med uppercase()
        //  2. Tenk en peu på korrigeringer av korrigeringer
    }
    fun ResultSet.toBehandlingshendelseRad() = InteressanteFelter(
        sekvensnummer = getLong("sekvensnummer"),
        data = objectMapper.readTree(getString("data")) as ObjectNode,
        siste = getBoolean("siste")
    )
}

private fun String.MEGA() = when(this) {
    "AvventerGodkjenning" -> "AVVENTER_GODKJENNING"
    else -> this.uppercase()
}

private fun InteressanteFelter.oppdaterFeltFor(feltnavn: String) {
    data.path(feltnavn).takeUnless { it.isMissingOrNull() }?.let {
        data.put(feltnavn, it.asText().MEGA())
    }
}

data class InteressanteFelter(
    val sekvensnummer: Long,
    val data: ObjectNode,
    val siste: Boolean
)