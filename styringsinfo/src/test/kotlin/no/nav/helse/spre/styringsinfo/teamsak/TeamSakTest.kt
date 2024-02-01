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
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*
import javax.sql.DataSource

internal class TeamSakTest: AbstractDatabaseTest() {

    private val behandlingDao: BehandlingDao = PostgresBehandlingDao(dataSource)

    @Test
    fun `funksjonell lik behandling`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        assertEquals(0, behandlingId.rader)
        generasjonOpprettet.håndter(behandlingDao)
        assertEquals(1, behandlingId.rader)

        val avsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        avsluttetMedVedtak.håndter(behandlingDao)
        assertEquals(2, behandlingId.rader)

        avsluttetMedVedtak.håndter(behandlingDao)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        val avsluttetMedVedtak = avsluttetMedVedtak(behandlingId)

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)

        avsluttetMedVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Vedtatt, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for auu`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Henlagt, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        val generasjonForkastet = generasjonForkastet(behandlingId)

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)

        generasjonForkastet.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Avbrutt, behandling.behandlingsresultat)
    }

    @Test
    fun `en annullering`() {
        val (januarBehandlingId, januarGenerasjonOpprettet, januarSakId) = generasjonOpprettet(Førstegangsbehandling)

        januarGenerasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(januarBehandlingId))
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(januarBehandlingId)
        januarAvsluttetMedVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(januarBehandlingId))
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)

        val (forkastetBehandlingId, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(TilInfotrygd)

        januarAnnullertGenerasjonOpprettet.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(forkastetBehandlingId))

        // TODO: Sjekk med David hvordan dette blir nå
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)
        assertEquals(Behandling.Behandlingstype.Førstegangsbehandling, behandling.behandlingstype)
        val generasjonForkastet = generasjonForkastet(forkastetBehandlingId)
        generasjonForkastet.håndter(behandlingDao)
    }

    @Test
    fun `en omgjøring av auu`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingDao)
        var behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        avsluttetUtenVedtak.håndter(behandlingDao)
        behandling = checkNotNull(behandlingDao.hent(behandlingId))
        assertEquals(Behandling.Behandlingstatus.Avsluttet, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.Henlagt, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, generasjonOpprettet2) = generasjonOpprettet(Omgjøring, sakId)
        generasjonOpprettet2.håndter(behandlingDao)

        val behandling2 = checkNotNull(behandlingDao.hent(behandlingId2))
        assertEquals(Behandling.Behandlingstatus.Registrert, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.Omgjøring, behandling2.behandlingstype)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

    @Test
    fun `case 1 lage litt eksempeldata til team sak`() {
        /*
        Scenario 1: en vedtaksperioder

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.

        */

        // generasjon opprettet med vedtak - januar

        val (behandlingId, januarGenerasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 1")
        januarGenerasjonOpprettet.håndter(behandlingDao)
        val januarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        januarAvsluttetMedVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 2 lage litt eksempeldata til team sak`() {
        /*
        Scenario 2: to vedtaksperioder, forlengelsen får én generasjon

        Bruker får sykmelding og sender søknad for januar. Arbeidsgiver sender inn inntektsmelding. Perioden utbetales.
        Bruker får sykmelding og sender søknad for februar. Perioden utbetales.
        Bruker sender korrigerende søknad med noen feriedager i februar. Perioden revurderes og utbetales.

        */
        // generasjon opprettet med vedtak - januar
        val (behandlingIdJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 2")
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingIdJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)

        // generasjon opprettet med vedtak - februar
        val (behandlingIdFebruar, februarGenerasjonOpprettet, sakIdFebruar) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 2")
        februarGenerasjonOpprettet.håndter(behandlingDao)

        val februarAvsluttetMedVedtak = avsluttetMedVedtak(behandlingIdFebruar)
        februarAvsluttetMedVedtak.håndter(behandlingDao)

        // generasjon opprettet med vedtak - februar igjen?
        val (andreGenerasjonFebruar, andreFebruarGenerasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 2", sakId = sakIdFebruar)
        andreFebruarGenerasjonOpprettet.håndter(behandlingDao)

        val andreFebruarAvsluttetMedVedtak = avsluttetMedVedtak(andreGenerasjonFebruar)
        andreFebruarAvsluttetMedVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 3 en enkel auu`() {
        // scenario 3: En enkel AUU

        // Bruker sender søknad som er helt innenfor arbeidsgiverperioden.

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 3")
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val januarAvsluttetUtenVedtak = avsluttetUtenVedtak(generasjonJanuar)
        januarAvsluttetUtenVedtak.håndter(behandlingDao)
    }

    @Test
    fun `case 4 en forkastet periode`() {
        // scenario 4: En enkel forkasting

        // Bruker sender søknad som inneholder detaljer vi ikke støtter i vedtaksløsningen enda.
        // Arbeidsgiver sender inntektsmelding.
        // Saken forkastes og løses i Infotrygd

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 4")
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val generasjonForkastet = generasjonForkastet(generasjonJanuar)
        generasjonForkastet.håndter(behandlingDao)
    }

    @Test
    fun `case 5 en annullert periode`() {
        // scenario 5: En enkel forkasting

        // Bruker sender søknad.
        // Arbeidsgiver sender inntektsmelding. Vedtaksperioden utbetales
        // Ny inntektsmelding betyr at saken ikke kan håndteres av ny vedtaksløsning livevel og saksbehandler annullerer

        // generasjon opprettet med vedtak - januar
        val (generasjonJanuar, januarGenerasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling, aktørId = "Scenario 5")
        januarGenerasjonOpprettet.håndter(behandlingDao)

        val januarAvsluttetMedVedtak = avsluttetMedVedtak(generasjonJanuar)
        januarAvsluttetMedVedtak.håndter(behandlingDao)

        val (forkastetGenerasjon, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(TilInfotrygd, aktørId = "Scenario 5", sakId = sakId)
        januarAnnullertGenerasjonOpprettet.håndter(behandlingDao)

        val generasjonForkastet = generasjonForkastet(forkastetGenerasjon)
        generasjonForkastet.håndter(behandlingDao)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandling;").asExecute)
        }
    }

    private val BehandlingId.rader get() =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandling where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

   internal companion object {
       private val nå = LocalDateTime.now()
       private var teller = 1L
       private val nesteTidspunkt get() = nå.plusDays(teller++)

       private val objectMapper = jacksonObjectMapper()
       private val blob = objectMapper.createObjectNode()

       private val Sykmeldt = GenerasjonOpprettet.Avsender("SYKMELDT")
       private val Arbeidsgiver = GenerasjonOpprettet.Avsender("ARBEIDSGIVER")
       private val Saksbehandler = GenerasjonOpprettet.Avsender("SAKSBEHANDLER")
       private val System = GenerasjonOpprettet.Avsender("SYSTEM")

       private val Førstegangsbehandling = GenerasjonOpprettet.Generasjonstype("Førstegangsbehandling")
       private val TilInfotrygd = GenerasjonOpprettet.Generasjonstype("TilInfotrygd")
       private val Omgjøring = GenerasjonOpprettet.Generasjonstype("Omgjøring")
       private val Revurdering = GenerasjonOpprettet.Generasjonstype("Revurdering")

       internal fun generasjonOpprettet(
           generasjonstype: GenerasjonOpprettet.Generasjonstype,
           sakId: SakId = SakId(UUID.randomUUID()),
           behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()),
           aktørId: String = "1234",
           avsender: GenerasjonOpprettet.Avsender = Sykmeldt
       ): Triple<BehandlingId, GenerasjonOpprettet, SakId> {
           val innsendt = nesteTidspunkt
           val registret = nesteTidspunkt
           val opprettet = nesteTidspunkt
           val generasjonkilde = GenerasjonOpprettet.Generasjonkilde(innsendt, registret, avsender)
           val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), opprettet, blob, sakId.id, behandlingId.id, aktørId, generasjonkilde, generasjonstype)
           return Triple(behandlingId, generasjonOpprettet, sakId)
       }
       internal fun avsluttetMedVedtak(behandlingId: BehandlingId) = AvsluttetMedVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun avsluttetUtenVedtak(behandlingId: BehandlingId) = AvsluttetUtenVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun generasjonForkastet(behandlingId: BehandlingId) = GenerasjonForkastet(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)

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
                               relatertBehandlingId = data.path("relatertBehandlingId").takeIf { it.isTextual }?.let { BehandlingId(UUID.fromString(it.asText())) },
                               aktørId = data.path("aktørId").asText(),
                               mottattTid = LocalDateTime.parse(data.path("mottattTid").asText()),
                               registrertTid = LocalDateTime.parse(data.path("registrertTid").asText()),
                               behandlingstatus = Behandling.Behandlingstatus.valueOf(data.path("behandlingstatus").asText()),
                               behandlingstype = Behandling.Behandlingstype.valueOf(data.path("behandlingtype").asText()),
                               behandlingsresultat = data.path("behandlingsresultat")?.takeIf { it.isTextual }?.let { Behandling.Behandlingsresultat.valueOf(it.asText()) },
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
       }
   }
}