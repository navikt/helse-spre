package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetMedVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonForkastet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeEndret
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.lang.System.getenv
import java.time.LocalDateTime
import java.util.UUID

internal class TeamSakTest: AbstractDatabaseTest() {

    private val hendelseDao: HendelseDao = PostgresHendelseDao(dataSource)
    private val behandlingshendelseDao: BehandlingshendelseDao = PostgresBehandlingshendelseDao(dataSource)

    @Test
    fun `funksjonell lik behandling`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)

        assertEquals(0, behandlingId.rader)
        generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(1, behandlingId.rader)

        val avsluttetMedVedtak = avsluttetMedVedtak(behandlingId)
        avsluttetMedVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(2, behandlingId.rader)

        avsluttetMedVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = vedtaksperiodeEndret(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVVENTER_GODKJENNING, behandling.behandlingstatus)

        behandling = avsluttetMedVedtak(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.VEDTAK_IVERKSATT, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for godkjent vedtak`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)

        vedtaksperiodeEndret(sakId).håndter(behandlingshendelseDao, behandlingId)

        var behandling = vedtaksperiodeGodkjent(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingsmetode.MANUELL, behandling.behandlingsmetode)
        assertEquals(VEDTATT, behandling.behandlingsresultat)

        behandling = avsluttetMedVedtak(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingsmetode.AUTOMATISK, behandling.behandlingsmetode)
    }

    @Test
    fun `start og slutt for avvist vedtak`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)

        vedtaksperiodeEndret(sakId).håndter(behandlingshendelseDao, behandlingId)

        var behandling = vedtaksperiodeAvvist(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingsmetode.MANUELL, behandling.behandlingsmetode)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertEquals("SB456", behandling.beslutterEnhet)

        behandling = avsluttetMedVedtak(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertEquals("SB456", behandling.beslutterEnhet)
    }

    @Test
    fun `presisjon på tidsstempler truncates ned til 6 desimaler i databasen`() {
        val tidspunkt = LocalDateTime.parse("2024-02-13T15:29:54.123123123")
        val (behandlingId, generasjonOpprettet, _) = generasjonOpprettet(Førstegangsbehandling, innsendt = tidspunkt, registrert = tidspunkt, opprettet = tidspunkt)
        generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)

        fun String.antallDesimaler() = if (this.contains(".")) this.split(".").last().length else 0
        assertEquals(6, funksjonellTid!!.antallDesimaler())
        assertEquals(6, mottattTid!!.antallDesimaler())
        assertEquals(6, registrertTid!!.antallDesimaler())
    }

    @Test
    fun `presisjon på tidsstempler justeres opp til 6 desimaler i databasen`() {
        val innsendt = LocalDateTime.parse("2024-02-13T15:29")
        val registrert = LocalDateTime.parse("2024-02-20T15:29")
        val opprettet = LocalDateTime.parse("2024-02-20T15:29:54.123")
        val (behandlingId, generasjonOpprettet, _) = generasjonOpprettet(Førstegangsbehandling, innsendt = innsendt, registrert = registrert, opprettet = opprettet)
        generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)

        fun String.antallDesimaler() = if (this.contains(".")) this.split(".").last().length else 0
        //assertEquals(6, funksjonellTid!!.antallDesimaler())
        assertEquals(6, mottattTid!!.antallDesimaler())
        assertEquals(6, registrertTid!!.antallDesimaler())
    }

    @Test
    fun `start og slutt for auu`() {
        val (behandlingId, generasjonOpprettet) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)
        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(sakId)
        behandling = generasjonForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val (januarBehandlingId, januarGenerasjonOpprettet, januarSakId) = generasjonOpprettet(Førstegangsbehandling)

        var utbetaltBehandling = januarGenerasjonOpprettet.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING, utbetaltBehandling.behandlingstype)
        assertNull(utbetaltBehandling.behandlingsresultat)
        assertEquals(utbetaltBehandling.behandlingsmetode, Behandling.Behandlingsmetode.AUTOMATISK)


        val januarAvsluttetMedVedtak = avsluttetMedVedtak(januarBehandlingId)
        utbetaltBehandling = januarAvsluttetMedVedtak.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.VEDTAK_IVERKSATT, utbetaltBehandling.behandlingsresultat)
        assertEquals(Behandling.Behandlingsmetode.AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        val (annulleringBehandlingId, januarAnnullertGenerasjonOpprettet) = generasjonOpprettet(TilInfotrygd, januarSakId, avsender = Saksbehandler)
        var annullertBehandling = januarAnnullertGenerasjonOpprettet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.SAKSBEHANDLER, annullertBehandling.behandlingskilde)
        assertNull(annullertBehandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(januarSakId)
        annullertBehandling = generasjonForkastet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        utbetaltBehandling = behandlingshendelseDao.hent(januarBehandlingId)!!

        assertEquals(3, januarBehandlingId.rader) // Registret, Vedtatt, Avbrutt
        assertEquals(2, annulleringBehandlingId.rader) // Registret, Avbrutt

        listOf(annullertBehandling, utbetaltBehandling).forEach {
            assertEquals(Behandling.Behandlingstatus.AVSLUTTET, it.behandlingstatus)
            assertEquals(Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING, it.behandlingstype)
            assertEquals(AVBRUTT, it.behandlingsresultat)
        }

        assertNull(utbetaltBehandling.behandlingsmetode)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(TilInfotrygd)
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val generasjonForkastet = generasjonForkastet(sakId)
        behandling = generasjonForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.FØRSTEGANGSBEHANDLING, behandling.behandlingstype)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `en omgjøring av auu`() {
        val (behandlingId, generasjonOpprettet, sakId) = generasjonOpprettet(Førstegangsbehandling)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = generasjonOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, generasjonOpprettet2) = generasjonOpprettet(Omgjøring, sakId, avsender = Arbeidsgiver)
        val behandling2 = generasjonOpprettet2.håndter(behandlingshendelseDao, behandlingId2)

        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.OMGJØRING, behandling2.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.ARBEIDSGIVER, behandling2.behandlingskilde)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

    @BeforeEach
    fun beforeEach() {
        sessionOf(dataSource).use { session ->
            session.run(queryOf("truncate table behandlingshendelse;").asExecute)
        }
    }

    @AfterEach
    fun afterEach() {
        if (getenv("CI") == "true") return
        alleRader.printTabell()
    }

    private val BehandlingId.rader get() =  sessionOf(dataSource).use { session ->
        session.run(queryOf("select count(1) from behandlingshendelse where behandlingId='$this'").map { row -> row.int(1) }.asSingle)
    } ?: 0

    private val alleRader get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select * from behandlingshendelse").map { row ->
            (objectMapper.readTree(row.string("data")) as ObjectNode).apply {
                put("sekvensnummer", row.long("sekvensnummer"))
                put("sakId", row.uuid("sakId").toString())
                put("behandlingId", row.uuid("behandlingId").toString())
                put("funksjonellTid", row.localDateTime("funksjonellTid").toString())
                put("tekniskTid", row.localDateTime("tekniskTid").toString())
                put("versjon", row.string("versjon"))
            }
        }.asList)
    }

    private val mottattTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select data->>'mottattTid' from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)
    }

    private val registrertTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select data->>'registrertTid' from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)
    }

    private val funksjonellTid get() = sessionOf(dataSource).use { session ->
        session.run(queryOf("select funksjonellTid from behandlingshendelse LIMIT 1").map { row ->
            row.string(1)
        }.asSingle)
    }

    private fun Hendelse.håndter(behandlingshendelseDao: BehandlingshendelseDao, behandlingId: BehandlingId): Behandling {
        hendelseDao.lagre(this)
        håndter(behandlingshendelseDao)
        return checkNotNull(behandlingshendelseDao.hent(behandlingId)) { "Fant ikke behandling $behandlingId" }
    }

    internal companion object {
       private val nå = LocalDateTime.now()
       private var teller = 1L
       private val nesteTidspunkt get() = nå.plusDays(teller++)

       private val objectMapper = jacksonObjectMapper()
       private val blob = objectMapper.createObjectNode()

       internal val Sykmeldt = GenerasjonOpprettet.Avsender("SYKMELDT")
       internal val Arbeidsgiver = GenerasjonOpprettet.Avsender("ARBEIDSGIVER")
       internal val Saksbehandler = GenerasjonOpprettet.Avsender("SAKSBEHANDLER")
       internal val System = GenerasjonOpprettet.Avsender("SYSTEM")

       internal val Førstegangsbehandling = GenerasjonOpprettet.Generasjonstype("Førstegangsbehandling")
       internal val TilInfotrygd = GenerasjonOpprettet.Generasjonstype("TilInfotrygd")
       internal val Omgjøring = GenerasjonOpprettet.Generasjonstype("Omgjøring")
       internal val Revurdering = GenerasjonOpprettet.Generasjonstype("Revurdering")


        private val String.printbar get() = take(25).padEnd(25, ' ') + "   "
        private fun List<ObjectNode>.printTabell() {
            println()
            println("********** Kul tabell til Team Sak **********")
            first().fieldNames().forEach { print(it.printbar) }
            println()
            forEach {
                it.fields().forEach { (_,verdi) -> print(verdi.asText().printbar) }
                println()
            }
            println()
        }

       internal fun generasjonOpprettet(
           generasjonstype: GenerasjonOpprettet.Generasjonstype,
           sakId: SakId = SakId(UUID.randomUUID()),
           behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()),
           aktørId: String = "1234",
           avsender: GenerasjonOpprettet.Avsender = Sykmeldt,
           innsendt: LocalDateTime = nesteTidspunkt,
           registrert: LocalDateTime = nesteTidspunkt,
           opprettet: LocalDateTime = nesteTidspunkt,
       ): Triple<BehandlingId, GenerasjonOpprettet, SakId> {
           val generasjonkilde = GenerasjonOpprettet.Generasjonkilde(innsendt, registrert, avsender)
           val generasjonOpprettet = GenerasjonOpprettet(UUID.randomUUID(), opprettet, blob, sakId.id, behandlingId.id, aktørId, generasjonkilde, generasjonstype)
           return Triple(behandlingId, generasjonOpprettet, sakId)
       }
       internal fun avsluttetMedVedtak(behandlingId: BehandlingId) = AvsluttetMedVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun avsluttetUtenVedtak(behandlingId: BehandlingId) = AvsluttetUtenVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun generasjonForkastet(sakId: SakId) = GenerasjonForkastet(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id)
       internal fun vedtaksperiodeEndret(sakId: SakId) = VedtaksperiodeEndret(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id)
       internal fun vedtaksperiodeGodkjent(sakId: SakId) = VedtaksperiodeBeslutning(UUID.randomUUID(),
           nesteTidspunkt,
           blob,
           sakId.id,
           "SB123",
           "SB456",
           false,
           VEDTATT,
           "vedtaksperiode_godkjent",
           Behandling.Behandlingstatus.GODKJENT)
       internal fun vedtaksperiodeAvvist(sakId: SakId) = VedtaksperiodeBeslutning(UUID.randomUUID(),
           nesteTidspunkt,
           blob,
           sakId.id,
           "SB123",
           "SB456",
           false,
           AVBRUTT,
           "vedtaksperiode_avvist",
           Behandling.Behandlingstatus.AVSLUTTET)
   }
}