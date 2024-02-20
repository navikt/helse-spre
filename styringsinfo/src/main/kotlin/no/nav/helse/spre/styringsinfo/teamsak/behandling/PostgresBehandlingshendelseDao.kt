package no.nav.helse.spre.styringsinfo.teamsak.behandling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.*
import org.intellij.lang.annotations.Language
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import javax.sql.DataSource

internal class PostgresBehandlingshendelseDao(private val dataSource: DataSource): BehandlingshendelseDao {
    override fun initialiser(behandlingId: BehandlingId) =
        hent(behandlingId)?.let { Behandling.Builder(it) }

    override fun initialiser(sakId: SakId): List<Behandling.Builder> {
        val sql = """
            select * from behandlingshendelse where sakId='${sakId}' and siste=true
        """
        return sessionOf(dataSource).use { session ->
            session.run(queryOf(sql).map { it.behandling }.asList)
        }.map { Behandling.Builder(it) }
    }

    override fun lagre(behandling: Behandling, hendelseId: UUID) {
        val behandlingId = behandling.behandlingId

        sessionOf(dataSource, strict = true).use { it.transaction { tx ->
            val sisteBehandling = tx.hent(behandlingId)
            if (sisteBehandling?.funksjoneltLik(behandling) == true) return@use
            val nySiste = when {
                sisteBehandling == null -> true // Første rad på denne behandlingen
                behandling.funksjonellTid > sisteBehandling.funksjonellTid -> true // Ny informasjon på behandlingen
                behandling.funksjonellTid == sisteBehandling.funksjonellTid -> true // Korrigerer siste rad
                else -> false // Korrigerer en tidligere rad
            }

            if (nySiste) tx.markerGamle(behandlingId)
            tx.lagre(behandling, nySiste, hendelseId)
        }}
    }

    private fun TransactionalSession.markerGamle(behandlingId: BehandlingId) {
        @Language("PostgreSQL") val markerGamle = """
            update behandlingshendelse set siste=false where behandlingId='${behandlingId}'
        """
        execute(queryOf(markerGamle))
    }

    private fun TransactionalSession.lagre(behandling: Behandling, siste: Boolean, hendelseId: UUID) {
        val sql = """
            insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert) 
            values(:sakId, :behandlingId, :funksjonellTid, :versjon, :data::jsonb, :siste, :hendelseId, false)
        """

        val data = objectMapper.createObjectNode().apply {
            put("aktørId", behandling.aktørId)
            put("mottattTid", behandling.mottattTid.format(formatter))
            put("registrertTid", behandling.registrertTid.format(formatter))
            put("behandlingstatus", behandling.behandlingstatus.name)
            put("behandlingtype", behandling.behandlingstype.name)
            put("behandlingskilde", behandling.behandlingskilde.name)
            putString("behandlingsmetode", behandling.behandlingsmetode?.name)
            putString("relatertBehandlingId", behandling.relatertBehandlingId?.toString())
            putString("behandlingsresultat", behandling.behandlingsresultat?.name)
            putString("saksbehandlerEnhet", behandling.saksbehandlerEnhet)
            putString("beslutterEnhet", behandling.beslutterEnhet)
        }

        val versjon = Versjon.of(data.felter)

        check(run(queryOf(sql, mapOf(
            "sakId" to behandling.sakId.id,
            "behandlingId" to behandling.behandlingId.id,
            "funksjonellTid" to behandling.funksjonellTid,
            "versjon" to versjon.toString(),
            "siste" to siste,
            "data" to data.toString(),
            "hendelseId" to hendelseId
        )).asUpdate) == 1) { "Forventet at en rad skulle legges til" }
    }

    override fun hent(behandlingId: BehandlingId) = sessionOf(dataSource, strict = true).use { session -> session.hent(behandlingId) }

    private fun Session.hent(behandlingId: BehandlingId): Behandling? {
        val sql = """
            select * from behandlingshendelse where behandlingId='${behandlingId}' and siste=true
        """
        return run(queryOf(sql).map { it.behandling }.asSingle)
    }

    private val Row.behandling get(): Behandling {
        val data = objectMapper.readTree(string("data"))
        return Behandling(
            sakId = SakId(uuid("sakId")),
            behandlingId = BehandlingId(uuid("behandlingId")),
            funksjonellTid = localDateTime("funksjonellTid"),
            relatertBehandlingId = data.path("relatertBehandlingId").uuidOrNull?.let { BehandlingId(it) },
            aktørId = data.path("aktørId").asText(),
            mottattTid = LocalDateTime.parse(data.path("mottattTid").asText()),
            registrertTid = LocalDateTime.parse(data.path("registrertTid").asText()),
            behandlingstatus = Behandling.Behandlingstatus.valueOf(data.path("behandlingstatus").asText()),
            behandlingstype = Behandling.Behandlingstype.valueOf(data.path("behandlingtype").asText()),
            behandlingsresultat = data.path("behandlingsresultat").textOrNull?.let { Behandling.Behandlingsresultat.valueOf(it) },
            behandlingskilde = Behandling.Behandlingskilde.valueOf(data.path("behandlingskilde").asText()),
            behandlingsmetode = data.path("behandlingsmetode").textOrNull?.let { Behandling.Behandlingsmetode.valueOf(it) }
        )
    }

    override fun forrigeBehandlingId(sakId: SakId): BehandlingId? {
        val sql = """
            select behandlingId from behandlingshendelse where sakId='${sakId}' and siste=true order by sekvensnummer desc limit 1
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

        val formatter = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS") // timestamps lagres med 6 desimaler i db

        private val JsonNode.textOrNull get() = takeIf { it.isTextual }?.asText()
        private val JsonNode.uuidOrNull get() = textOrNull?.let { UUID.fromString(it) }
        private fun ObjectNode.putString(fieldName: String, value: String?) {
            if (value == null) putNull(fieldName)
            else put(fieldName, value)
        }
        private val ObjectNode.felter get() = fieldNames().asSequence().toSet()
    }
}
