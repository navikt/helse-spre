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
import java.time.Month
import java.util.*
import javax.sql.DataSource

internal class TeamSakTest: AbstractDatabaseTest() {

    private val behandlingDao: PostgresBehandlingDao = PostgresBehandlingDao(dataSource)

    @Test
    fun `start og slutt for vedtak`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert, Behandling.Behandlingstype.Førstegangsbehandling)

        val avsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.KomplettFraBruker, behandling.behandlingstatus)

        avsluttetMedVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.AvsluttetMedVedtak, behandling.behandlingstatus)
    }

    @Test
    fun `start og slutt for auu`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert, Behandling.Behandlingstype.Førstegangsbehandling)

        val avsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.KomplettFraBruker, behandling.behandlingstatus)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.AvsluttetUtenVedtak, behandling.behandlingstatus)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert, Behandling.Behandlingstype.Førstegangsbehandling)

        val generasjonForkastet = GenerasjonForkastet(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)
        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.KomplettFraBruker, behandling.behandlingstatus)

        generasjonForkastet.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.BehandlesIInfotrygd, behandling.behandlingstatus)
    }

    @Test
    fun `en omgjøring av auu`() {
        val vedtaksperiodeId = UUID.randomUUID()
        val generasjonId = UUID.randomUUID()
        val aktørId = "1234"
        val innsendt = LocalDateTime.now()
        val registrert = innsendt.plusDays(1)

        val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId, aktørId, innsendt, registrert, Behandling.Behandlingstype.Førstegangsbehandling)

        val avsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), LocalDateTime.now(), blob, generasjonId)

        val behandlingId = generasjonId.behandlingId

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.KomplettFraBruker, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.AvsluttetUtenVedtak, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        val generasjonId2 = UUID.randomUUID()
        val generasjonOpprettet2 = GenerasjonOpprettet(UUID.randomUUID(), LocalDateTime.now(),
            blob, vedtaksperiodeId, generasjonId2, aktørId, innsendt, registrert, Behandling.Behandlingstype.Omgjøring)
        generasjonOpprettet2.håndter(behandlingDao)

        val behandlingId2 = generasjonId2.behandlingId
        val behandling2 = checkNotNull(behandlingDao.hent(behandlingId2))
        assertEquals(Behandling.Behandlingstatus.KomplettFraBruker, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Omgjøring, behandling2.behandlingstype)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

    @Test
    fun `case 1 lage litt eksempeldata til team sak`() {
        val day_zero = LocalDateTime.of(2024, Month.FEBRUARY, 28, 13, 0)
        behandlingDao.ryddOpp()
        /*
        Scenario 1: en vedtaksperioder

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.

        */
        val aktørId = "Scenario 1"

        // generasjon opprettet med vedtak - januar
        val vedtaksperiodeJanuar = UUID.randomUUID()
        val generasjonJanuar = UUID.randomUUID()
        val januarSøknadInnsendt = day_zero
        val januarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusHours(1),
            blob, vedtaksperiodeJanuar, generasjonJanuar, aktørId, januarSøknadInnsendt, januarSøknadInnsendt, Behandling.Behandlingstype.Førstegangsbehandling)
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val januarAvsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), day_zero.plusHours(2), blob, generasjonJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 2 lage litt eksempeldata til team sak`() {
        val day_zero = LocalDateTime.of(2024, Month.FEBRUARY, 28, 13, 0)
        behandlingDao.ryddOpp()
        /*
        Scenario 2: to vedtaksperioder, forlengelsen får én generasjon

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.
        Bruker får sykmelding og sender søknad for februar. Perioden utbetales.
        Bruker sender korrigerende søknad med noen feriedager i februar. Perioden revurderes og utbetales.

        */
        val aktørId = "Scenario 2"

        // generasjon opprettet med vedtak - januar
        val vedtaksperiodeJanuar = UUID.randomUUID()
        val generasjonJanuar = UUID.randomUUID()
        val januarSøknadInnsendt = day_zero
        val januarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusHours(1),
            blob, vedtaksperiodeJanuar, generasjonJanuar, aktørId, januarSøknadInnsendt, januarSøknadInnsendt, Behandling.Behandlingstype.Førstegangsbehandling)
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val januarAvsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), day_zero.plusHours(2), blob, generasjonJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)

        // generasjon opprettet med vedtak - februar
        val vedtaksperiodeFebruar = UUID.randomUUID()
        val førsteGenerasjonFebruar = UUID.randomUUID()
        val februarSøknadInnsendt = day_zero.plusWeeks(4)
        val februarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusWeeks(4).plusHours(1),
            blob, vedtaksperiodeFebruar, førsteGenerasjonFebruar, aktørId, februarSøknadInnsendt, februarSøknadInnsendt, Behandling.Behandlingstype.Førstegangsbehandling)
        februarGenerasjonOpprettet.håndter(behandlingDao)
        val februarAvsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), day_zero.plusWeeks(4).plusHours(2), blob, førsteGenerasjonFebruar)
        februarAvsluttetMedVedtak.håndter(behandlingDao)

        // generasjon opprettet med vedtak - februar igjen?
        val andreGenerasjonFebruar = UUID.randomUUID()
        val februarKorrigerendeSøknadInnsendt = day_zero.plusWeeks(4).plusHours(3)
        val andreFebruarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusWeeks(4).plusHours(3),
            blob, vedtaksperiodeFebruar, andreGenerasjonFebruar, aktørId, februarKorrigerendeSøknadInnsendt, februarKorrigerendeSøknadInnsendt, Behandling.Behandlingstype.Revurdering)
        andreFebruarGenerasjonOpprettet.håndter(behandlingDao)
        val andreFebruarAvsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), day_zero.plusWeeks(4).plusHours(4), blob, andreGenerasjonFebruar)
        andreFebruarAvsluttetMedVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 3 en enkel auu`() {
        val day_zero = LocalDateTime.of(2024, Month.FEBRUARY, 28, 13, 0)
        behandlingDao.ryddOpp()
        // scenario 3: En enkel AUU

        // Bruker sender søknad som er helt innenfor arbeidsgiverperioden.
        val aktørId = "Scenario 3"

        // generasjon opprettet med vedtak - januar
        val vedtaksperiodeJanuar = UUID.randomUUID()
        val generasjonJanuar = UUID.randomUUID()
        val januarSøknadInnsendt = day_zero
        val januarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusHours(1),
            blob, vedtaksperiodeJanuar, generasjonJanuar, aktørId, januarSøknadInnsendt, januarSøknadInnsendt, Behandling.Behandlingstype.Førstegangsbehandling)
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val januarAvsluttetUtenVedtak = AvsluttetUtenVedtak(UUID.randomUUID(), day_zero.plusHours(2), blob, generasjonJanuar)
        januarAvsluttetUtenVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 4 en forkastet periode`() {
        val day_zero = LocalDateTime.of(2024, Month.FEBRUARY, 28, 13, 0)
        behandlingDao.ryddOpp()
        // scenario 4: En enkel forkasting

        // Bruker sender søknad som inneholder detaljer vi ikke støtter i vedtaksløsningen enda.
        // Arbeidsgiver sender inntektsmelding.
        // Saken forkastes og løses i Infotrygd
        val aktørId = "Scenario 4"

        // generasjon opprettet med vedtak - januar
        val vedtaksperiodeJanuar = UUID.randomUUID()
        val generasjonJanuar = UUID.randomUUID()
        val januarSøknadInnsendt = day_zero
        val januarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusHours(1),
            blob, vedtaksperiodeJanuar, generasjonJanuar, aktørId, januarSøknadInnsendt, januarSøknadInnsendt, Behandling.Behandlingstype.Førstegangsbehandling)
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val generasjonForkastet = GenerasjonForkastet(UUID.randomUUID(), day_zero.plusHours(2), blob, generasjonJanuar)
        generasjonForkastet.håndter(behandlingDao)
    }

    @Test
    fun `case 5 en annullert periode`() {
        val day_zero = LocalDateTime.of(2024, Month.FEBRUARY, 28, 13, 0)
        behandlingDao.ryddOpp()
        // scenario 5: En enkel forkasting

        // Bruker sender søknad.
        // Arbeidsgiver sender inntektsmelding. Vedtaksperioden utbetales
        // Ny inntektsmelding betyr at saken ikke kan håndteres av ny vedtaksløsning livevel og saksbehandler annullerer
        val aktørId = "Scenario 5"

        // generasjon opprettet med vedtak - januar
        val vedtaksperiodeJanuar = UUID.randomUUID()
        val generasjonJanuar = UUID.randomUUID()
        val januarSøknadInnsendt = day_zero
        val januarGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusHours(1), blob, vedtaksperiodeJanuar, generasjonJanuar, aktørId, januarSøknadInnsendt, januarSøknadInnsendt, Behandling.Behandlingstype.Førstegangsbehandling)
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val januarAvsluttetMedVedtak = AvsluttetMedVedtak(UUID.randomUUID(), day_zero.plusHours(2), blob, generasjonJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)

        val forkastetGenerasjon = UUID.randomUUID()
        val januarAnnullertGenerasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), day_zero.plusHours(1), blob, vedtaksperiodeJanuar, forkastetGenerasjon, aktørId, januarSøknadInnsendt, januarSøknadInnsendt, Behandling.Behandlingstype.TilInfotrygd)
        januarAnnullertGenerasjonOpprettet.håndter(behandlingDao)
    }

   internal companion object {
       private val blob = jacksonObjectMapper().createObjectNode()
       internal val UUID.behandlingId get() = BehandlingId(this)

       internal class PostgresBehandlingDao(private val dataSource: DataSource): BehandlingDao {
           override fun initialiser(behandlingId: BehandlingId): Behandling.Builder? {
               return hent(behandlingId)?.let { Behandling.Builder(it) }
           }

           fun ryddOpp() {
               val sql ="""
                   truncate table behandling;
               """.trimIndent()
               sessionOf(dataSource).use { session ->
                   session.run(queryOf(sql).asExecute)
               }
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
                   put("behandlingStatus", behandling.behandlingstatus.name)
                   put("behandlingType", behandling.behandlingstype.name)
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
                   select * from behandling where behandlingId='${behandlingId}' order by funksjonellTid, tekniskTid desc limit 1
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
                               relatertBehandlingId = data.path("relatertBehandlingId").takeIf { it.isTextual }?.let { BehandlingId(UUID.fromString(it.asText())) },
                               aktørId = data.path("aktørId").asText(),
                               mottattTid = LocalDateTime.parse(data.path("mottattTid").asText()),
                               registrertTid = LocalDateTime.parse(data.path("registrertTid").asText()),
                               behandlingstatus = Behandling.Behandlingstatus.valueOf(data.path("behandlingStatus").asText()),
                               behandlingstype = Behandling.Behandlingstype.valueOf(data.path("behandlingType").asText())
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
           }
       }
   }
}