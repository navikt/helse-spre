package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class PostgresBehandlingDao(private val dataSource: DataSource): BehandlingDao {
    override fun initialiser(behandlingId: BehandlingId) =
        hent(behandlingId)?.let { Behandling.Builder(it) }

    override fun initialiser(sakId: SakId): List<Behandling.Builder> {
        val sql = """
            select * from behandling where sakId='${sakId}' and siste=true
        """
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(sql).map { it.behandling }.asList)
        }.map { Behandling.Builder(it) }
    }

    override fun lagre(behandling: Behandling) {
        val behandlingId = behandling.behandlingId

        sessionOf(dataSource).use { it.transaction { tx ->
            val sisteBehandling = tx.hent(behandlingId)
            if (sisteBehandling?.funksjoneltLik(behandling) == true) return@use
            val nySiste = when {
                sisteBehandling == null -> true // Første rad på denne behandlingen
                behandling.funksjonellTid > sisteBehandling.funksjonellTid -> true // Ny informasjon på behandlingen
                behandling.funksjonellTid == sisteBehandling.funksjonellTid -> true // Korrigerer siste rad
                else -> false // Korrigerer en tidligere rad
            }

            if (nySiste) tx.markerGamle(behandlingId)
            tx.lagre(behandling, nySiste)
        }}
    }

    private fun TransactionalSession.markerGamle(behandlingId: BehandlingId) {
        @Language("PostgreSQL") val markerGamle = """
            update behandling set siste=false where behandlingId='${behandlingId}'
        """
        execute(queryOf(markerGamle))
    }

    private fun TransactionalSession.lagre(behandling: Behandling, siste: Boolean) {
        val sql = """
            insert into behandling(sakId, behandlingId, funksjonellTid, versjon, data, siste) 
            values(:sakId, :behandlingId, :funksjonellTid, :versjon, :data::jsonb, :siste)
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

        check(run(queryOf(sql, mapOf(
            "sakId" to behandling.sakId.id,
            "behandlingId" to behandling.behandlingId.id,
            "funksjonellTid" to behandling.funksjonellTid,
            "versjon" to behandling.versjon.toString(),
            "siste" to siste,
            "data" to data
        )).asUpdate) == 1) { "Forventet at en rad skulle legges til" }
    }

    override fun hent(behandlingId: BehandlingId) = sessionOf(dataSource).use { session -> session.hent(behandlingId) }

    private fun Session.hent(behandlingId: BehandlingId): Behandling? {
        val sql = """
            select * from behandling where behandlingId='${behandlingId}' and siste=true
        """
        return run(queryOf(sql).map { it.behandling }.asSingle)
    }

    private val Row.behandling get(): Behandling {
        val data = objectMapper.readTree(string("data"))
        return Behandling(
            sakId = SakId(uuid("sakId")),
            behandlingId = BehandlingId(uuid("behandlingId")),
            funksjonellTid = localDateTime("funksjonellTid"),
            versjon = Versjon.of(string("versjon")),
            relatertBehandlingId = data.path("relatertBehandlingId").uuidOrNull?.let { BehandlingId(it) },
            aktørId = data.path("aktørId").asText(),
            mottattTid = LocalDateTime.parse(data.path("mottattTid").asText()),
            registrertTid = LocalDateTime.parse(data.path("registrertTid").asText()),
            behandlingstatus = Behandling.Behandlingstatus.valueOf(data.path("behandlingstatus").asText()),
            behandlingstype = Behandling.Behandlingstype.valueOf(data.path("behandlingtype").asText()),
            behandlingsresultat = data.path("behandlingsresultat").textOrNull?.let { Behandling.Behandlingsresultat.valueOf(it) },
            behandlingskilde = Behandling.Behandlingskilde.valueOf(data.path("behandlingskilde").asText())
        )
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
