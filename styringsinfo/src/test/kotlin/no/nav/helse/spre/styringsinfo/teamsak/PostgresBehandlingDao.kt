package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class PostgresBehandlingDao(private val dataSource: DataSource): BehandlingDao {
    override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? {
        return hent(behandlingId)?.let { Behandling.Builder(it) }
    }

    override fun lagre(behandling: Behandling) {
        val sql = """
            insert into behandling(sakId, behandlingId, funksjonellTid, tekniskTid, versjon, data) 
            values(:sakId, :behandlingId, :funksjonellTid, :tekniskTid, :versjon, :data::jsonb)
        """

        val data = objectMapper.createObjectNode().apply {
            put("aktørId", behandling.aktørId)
            put("mottattTid", "${behandling.mottattTid}")
            put("registrertTid", "${behandling.registrertTid}")
            put("behandlingstatus", behandling.behandlingstatus.name)
            put("behandlingtype", behandling.behandlingstype.name)
            put("behandlingskilde", behandling.behandlingskilde.name)
            behandling.relatertBehandlingId?.let { put("relatertBehandlingId", "$it") }
            behandling.behandlingsresultat?.let { put("behandlingsresultat", it.name) }
        }.toString()

        sessionOf(dataSource).use { session ->
            session.run(
                queryOf(sql, mapOf(
                    "sakId" to behandling.sakId.id,
                    "behandlingId" to behandling.behandlingId.id,
                    "funksjonellTid" to behandling.funksjonellTid,
                    "tekniskTid" to behandling.tekniskTid,
                    "versjon" to behandling.versjon.toString(),
                    "data" to data
                )).asUpdate
            )
        }
    }

    override fun hent(behandlingId: BehandlingId): Behandling? {
        val sql = """
            select * from behandling where behandlingId='${behandlingId}' order by funksjonellTid desc, tekniskTid desc limit 1
        """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(sql).map { row ->
                    val data = objectMapper.readTree(row.string("data"))
                    Behandling(
                        sakId = SakId(row.uuid("sakId")),
                        behandlingId = BehandlingId(row.uuid("behandlingId")),
                        funksjonellTid = row.localDateTime("funksjonellTid"),
                        tekniskTid = row.localDateTime("tekniskTid"),
                        versjon = Versjon.of(row.string("versjon")),
                        relatertBehandlingId = data.path("relatertBehandlingId").uuidOrNull?.let { BehandlingId(it) },
                        aktørId = data.path("aktørId").asText(),
                        mottattTid = LocalDateTime.parse(data.path("mottattTid").asText()),
                        registrertTid = LocalDateTime.parse(data.path("registrertTid").asText()),
                        behandlingstatus = Behandling.Behandlingstatus.valueOf(data.path("behandlingstatus").asText()),
                        behandlingstype = Behandling.Behandlingstype.valueOf(data.path("behandlingtype").asText()),
                        behandlingsresultat = data.path("behandlingsresultat").textOrNull?.let { Behandling.Behandlingsresultat.valueOf(it) },
                        behandlingskilde = Behandling.Behandlingskilde.valueOf(data.path("behandlingskilde").asText())
                    )
                }.asSingle)
        }
    }

    override fun forrigeBehandlingId(sakId: SakId): BehandlingId? {
        val sql = """
            select behandlingId from behandling where sakId='${sakId}' order by funksjonellTid, tekniskTid desc limit 1
        """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(sql).map { row ->
                    BehandlingId(row.uuid("behandlingId"))
                }.asSingle)
        }
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
        private val JsonNode.textOrNull get() = takeIf { it.isTextual }?.asText()
        private val JsonNode.uuidOrNull get() = textOrNull?.let { UUID.fromString(it) }
    }
}
