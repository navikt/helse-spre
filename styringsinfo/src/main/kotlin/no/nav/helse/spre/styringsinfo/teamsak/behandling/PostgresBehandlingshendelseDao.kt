package no.nav.helse.spre.styringsinfo.teamsak.behandling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.*
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatterBuilder
import java.util.*
import javax.sql.DataSource

internal class PostgresBehandlingshendelseDao(private val dataSource: DataSource): BehandlingshendelseDao {

    override fun initialiser(behandlingId: BehandlingId) =
        hent(behandlingId)?.let { Behandling.Builder(it) }

    override fun lagre(behandling: Behandling, hendelseId: UUID): Boolean {
        sessionOf(dataSource, strict = true).use { it.transaction { tx ->
            if (!tx.kanLagres(behandling, hendelseId)) return false
            tx.markerGamle(behandling.behandlingId)
            tx.lagre(behandling, hendelseId)
        }}
        return true
    }

    private fun TransactionalSession.kanLagres(behandling: Behandling, hendelseId: UUID): Boolean {
        @Language("PostgreSQL") val antallRaderMedLikEllerSenereFunksjonellTidQuery = """
            select count(1) from behandlingshendelse where behandlingId = :behandlingId AND funksjonellTid >= :funksjonellTid
        """
        val antallRaderMedLikEllerSenereFunksjonellTid = run(queryOf(antallRaderMedLikEllerSenereFunksjonellTidQuery, mapOf(
            "behandlingId" to behandling.behandlingId.id,
            "funksjonellTid" to behandling.funksjonellTid)
        ).map { it.int(1) }.asSingle) ?: 0
        val kanLagres = antallRaderMedLikEllerSenereFunksjonellTid == 0
        if (!kanLagres) logger.warn("Lagrer _ikke_ ny rad for sak ${behandling.sakId}, behandling ${behandling.behandlingId} fra hendelse $hendelseId. Det finnes allerede $antallRaderMedLikEllerSenereFunksjonellTid rader med funksjonellTid >= ${behandling.funksjonellTid}")
        return kanLagres
    }

    private fun TransactionalSession.markerGamle(behandlingId: BehandlingId) {
        @Language("PostgreSQL") val markerGamle = """
            update behandlingshendelse set siste=false where behandlingId='${behandlingId}'
        """
        execute(queryOf(markerGamle))
    }

    private fun TransactionalSession.lagre(behandling: Behandling, hendelseId: UUID) {
        val sql = """
            insert into behandlingshendelse(sakId, behandlingId, funksjonellTid, versjon, data, siste, hendelseId, er_korrigert) 
            values(:sakId, :behandlingId, :funksjonellTid, :versjon, :data::jsonb, true, :hendelseId, false)
        """

        val data = objectMapper.createObjectNode().apply {
            put("aktørId", behandling.aktørId)
            put("mottattTid", tilJson(behandling.mottattTid))
            put("registrertTid", tilJson(behandling.registrertTid))
            put("behandlingstatus", behandling.behandlingstatus.name)
            put("behandlingstype", behandling.behandlingstype.name)
            put("behandlingskilde", behandling.behandlingskilde.name)
            put("hendelsesmetode", behandling.hendelsesmetode.name)
            put("behandlingsmetode", behandling.behandlingsmetode.name)
            putString("relatertBehandlingId", behandling.relatertBehandlingId?.toString())
            putString("behandlingsresultat", behandling.behandlingsresultat?.name)
            putString("periodetype", behandling.periodetype?.name)
            putString("saksbehandlerEnhet", behandling.saksbehandlerEnhet)
            putString("beslutterEnhet", behandling.beslutterEnhet)
            putString("mottaker", behandling.mottaker?.toString())
        }

        val versjon = Versjon.of(data.felter)

        check(run(queryOf(sql, mapOf(
            "sakId" to behandling.sakId.id,
            "behandlingId" to behandling.behandlingId.id,
            "funksjonellTid" to behandling.funksjonellTid,
            "versjon" to versjon.toString(),
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
            relatertBehandlingId = data.path("relatertBehandlingId").uuidOrNull?.let { BehandlingId(it) },
            aktørId = data.path("aktørId").asText(),
            mottattTid = fraJson(data.path("mottattTid")),
            registrertTid = fraJson(data.path("registrertTid")),
            funksjonellTid = offsetDateTime("funksjonellTid"),
            behandlingstatus = Behandling.Behandlingstatus.valueOf(data.path("behandlingstatus").asText()),
            behandlingstype = Behandling.Behandlingstype.valueOf(data.path("behandlingstype").asText()),
            periodetype = data.path("periodetype").textOrNull?.let { Behandling.Periodetype.valueOf(it) },
            behandlingsresultat = data.path("behandlingsresultat").textOrNull?.let { Behandling.Behandlingsresultat.valueOf(it) },
            behandlingskilde = Behandling.Behandlingskilde.valueOf(data.path("behandlingskilde").asText()),
            behandlingsmetode = Behandling.Metode.valueOf(data.path("behandlingsmetode").asText()),
            hendelsesmetode = Behandling.Metode.valueOf(data.path("hendelsesmetode").asText()),
            saksbehandlerEnhet = data.path("saksbehandlerEnhet").textOrNull,
            beslutterEnhet = data.path("beslutterEnhet").textOrNull,
            mottaker = data.path("mottaker").textOrNull?.let { Behandling.Mottaker.valueOf(it) }
        )
    }

    override fun sisteBehandlingId(sakId: SakId): BehandlingId? {
        val sql = """
            select behandlingId from behandlingshendelse where sakId='${sakId}' and siste=true order by funksjonelltid desc limit 1
        """
        return sessionOf(dataSource).use { session ->
            session.run(
                queryOf(sql).map { row ->
                    BehandlingId(row.uuid("behandlingId"))
                }.asSingle)
        }
    }

    override fun harHåndtertHendelseTidligere(hendelseId: UUID): Boolean {
        val sql = """
            select count(1) from behandlingshendelse where hendelseid='$hendelseId'
        """
        return (sessionOf(dataSource).use { session ->
            session.run(
                queryOf(sql).map { it.int(1) }.asSingle
            ) ?: 0
        }) > 0
    }

    private companion object {
        private val logger = LoggerFactory.getLogger(PostgresBehandlingshendelseDao::class.java)
        private val objectMapper = jacksonObjectMapper()

        private val JsonNode.textOrNull get() = takeIf { it.isTextual }?.asText()
        private val JsonNode.uuidOrNull get() = textOrNull?.let { UUID.fromString(it) }
        private fun ObjectNode.putString(fieldName: String, value: String?) {
            if (value == null) putNull(fieldName)
            else put(fieldName, value)
        }
        private val ObjectNode.felter get() = fieldNames().asSequence().toSet()

        private val formatter = DateTimeFormatterBuilder().appendPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS").appendOffsetId().toFormatter() // timestamps lagres med 6 desimaler + offset i db
        private fun fraJson(jsonNode: JsonNode) = OffsetDateTime.parse(jsonNode.asText())
        private fun tilJson(tidspunkt: OffsetDateTime) = tidspunkt.format(formatter)
    }
}
