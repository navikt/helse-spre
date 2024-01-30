package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetMedVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonForkastet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class TeamSakTest: AbstractDatabaseTest() {

    private val behandlingDao: BehandlingDao = PostgresBehandlingDao(dataSource)

    @Test
    fun `start og slutt for vedtak`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val avsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)

        avsluttetMedVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.AvsluttetMedVedtak, behandling.behandlingStatus)
    }

    @Test
    fun `start og slutt for auu`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val avsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.AvsluttetUtenVedtak, behandling.behandlingStatus)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val generasjonForkastet = GenerasjonForkastet(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)
        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)

        generasjonForkastet.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.BehandlesIInfotrygd, behandling.behandlingStatus)
    }

    @Test
    fun `en omgjøring av auu`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert)

        val avsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling.behandlingStatus)
        assertNull(behandling.relatertBehandlingId)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.BehandlingStatus.AvsluttetUtenVedtak, behandling.behandlingStatus)
        assertNull(behandling.relatertBehandlingId)

        val generasjonId2 = UUID.randomUUID()
        val generasjonOpprettet2 = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId2, aktørId, innsendt, registrert)
        generasjonOpprettet2.håndter(behandlingDao)

        val behandlingId2 = generasjonId2.behandlingId
        val behandling2 = checkNotNull(behandlingDao.hent(behandlingId2))
        assertEquals(Behandling.BehandlingStatus.KomplettFraBruker, behandling2.behandlingStatus)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

   internal companion object {
       val blob = jacksonObjectMapper().createObjectNode()
       internal val UUID.behandlingId get() = BehandlingId(this)
       internal class InMemoryBehandlingDao: BehandlingDao {
           private val behandlinger = mutableListOf<Behandling>()
           override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? {
               val siste = hent(behandlingId) ?: return null
               return Behandling.Builder(siste)
           }
           override fun lagre(behandling: Behandling) {
               behandlinger.add(behandling)
           }
           override fun hent(behandlingId: BehandlingId) = behandlinger.lastOrNull { it.behandlingId == behandlingId }
           override fun forrigeBehandlingId(sakId: SakId) = behandlinger.lastOrNull { it.sakId == sakId }?.behandlingId
       }

       internal class PostgresBehandlingDao(private val dataSource: DataSource): BehandlingDao {
           override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? {
               return hent(behandlingId)?.let { Behandling.Builder(it) }
           }

           override fun lagre(behandling: Behandling) {
               val sql = """
                   insert into behandling(sakId, behandlingId, funksjonellTid, tekniskTid, versjon, data) 
                   values(:sakId, :behandlingId, :funksjonellTid, :tekniskTid, :versjon, :data::jsonb)
               """

               val data = jacksonObjectMapper().createObjectNode().apply {
                   put("aktørId", behandling.aktørId)
                   put("mottattTid", "${behandling.mottattTid}")
                   put("registrertTid", "${behandling.registrertTid}")
                   put("behandlingStatus", behandling.behandlingStatus.name)
                   behandling.relatertBehandlingId?.let { put("relatertBehandlingId", "$it") }
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
                   select * from behandling where behandlingId='${behandlingId}' --order by funksjonellTid desc, tekniskTid desc limit 1
               """
               val behandlinger =  sessionOf(dataSource).use { session ->
                   session.run(
                       queryOf(sql).map { row ->
                           val data = objectMapper.readTree(row.string("data"))
                           Behandling(
                               sakId = SakId(row.uuid("sakId")),
                               behandlingId = BehandlingId(row.uuid("behandlingId")),
                               funksjonellTid = row.localDateTime("funksjonellTid"),
                               tekniskTid = row.localDateTime("tekniskTid"),
                               versjon = Versjon.of(row.string("versjon")),
                               relatertBehandlingId = data.path("relatertBehandlingId").takeIf { it.isTextual }?.let { BehandlingId(UUID.fromString(it.asText())) },
                               aktørId = data.path("aktørId").asText(),
                               mottattTid = LocalDateTime.parse(data.path("mottattTid").asText()),
                               registrertTid = LocalDateTime.parse(data.path("registrertTid").asText()),
                               behandlingStatus = Behandling.BehandlingStatus.valueOf(data.path("behandlingStatus").asText())
                           )
                       }.asList)
               }
               return behandlinger.maxByOrNull { it.tekniskTid } // TODO: Skjønne seg på hvorfor den order by-saken ikke gjorde susen. Bruke asSingle istedenfor asList 🤔
           }

           override fun forrigeBehandlingId(sakId: SakId): BehandlingId? {
               val sql = """
                   select behandlingId from behandling where sakId='${sakId}' order by funksjonellTid desc, tekniskTid desc limit 1
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
           }
       }
   }
}