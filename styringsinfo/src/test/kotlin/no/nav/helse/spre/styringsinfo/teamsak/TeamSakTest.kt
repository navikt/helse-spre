package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.VURDERER_INNGANGSVILKÅR
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FORLENGELSE
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.*
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.AvsluttetUtenVedtak
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.BehandlingOpprettet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeBeslutning
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeEndretTilGodkjenning
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeEndretTilVilkårsprøving
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
        val (behandlingId, behandlingOpprettet) = behandlingIdOpprettet(Søknad)

        assertEquals(0, behandlingId.rader)
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(1, behandlingId.rader)

        val vedtakFattet = vedtakFattet(behandlingId)
        vedtakFattet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(2, behandlingId.rader)

        vedtakFattet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = vedtaksperiodeEndretTilGodkjenning(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)

        behandling = vedtakFattet(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.VEDTAK_IVERKSATT, behandling.behandlingsresultat)
    }

    @Test
    fun `periodetype blir førstegangsbehandling for perioder som vilkårsprøves`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        val behandling = vedtaksperiodeEndretTilVilkårsprøving(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(VURDERER_INNGANGSVILKÅR, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
    }

    @Test
    fun `førstegangsbehandling revurderes`() {
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang, sakIdFørstegang) = behandlingIdOpprettet(Søknad)
        behandlingOpprettetFørstegang.håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeEndretTilVilkårsprøving(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeEndretTilGodkjenning(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeGodkjent(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        val behandling = vedtakFattet(førstegangsbehandlingId).håndter(behandlingshendelseDao, førstegangsbehandlingId)

        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)

        val (revurderingbehandlingId, behandlingOpprettetRevurdering, sakIdRevurdering) = behandlingIdOpprettet(Søknad, sakIdFørstegang)
        behandlingOpprettetRevurdering.håndter(behandlingshendelseDao, revurderingbehandlingId)
        val behandlingRevurdering = vedtaksperiodeEndretTilGodkjenning(sakIdRevurdering).håndter(behandlingshendelseDao, revurderingbehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandlingRevurdering.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandlingRevurdering.periodetype)
    }

    @Test
    fun `peridodetype blir forlengelse ved godkjenning dersom ingen tidligere hendelse på behandlingen er markert som førstegangsbehandling`() {
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang, sakIdFørstegang) = behandlingIdOpprettet(Søknad)
        behandlingOpprettetFørstegang.håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeEndretTilVilkårsprøving(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeEndretTilGodkjenning(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeGodkjent(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtakFattet(førstegangsbehandlingId).håndter(behandlingshendelseDao, førstegangsbehandlingId)

        val (forlengelseBehandlingId, behandlingOpprettetForlengelse, sakIdForlengelse) = behandlingIdOpprettet(Søknad)
        behandlingOpprettetForlengelse.håndter(behandlingshendelseDao, forlengelseBehandlingId)
        val behandling = vedtaksperiodeEndretTilGodkjenning(sakIdForlengelse).håndter(behandlingshendelseDao, forlengelseBehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FORLENGELSE, behandling.periodetype)
    }

    @Test
    fun `periodetype går fra førstegangsbehandling til forlengelse ved et snasent out-of-order-scenario`() {
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang, sakIdFørstegang) = behandlingIdOpprettet(Søknad)
        behandlingOpprettetFørstegang.håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeEndretTilVilkårsprøving(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeEndretTilGodkjenning(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        vedtaksperiodeGodkjent(sakIdFørstegang).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        val behandling = vedtakFattet(førstegangsbehandlingId).håndter(behandlingshendelseDao, førstegangsbehandlingId)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)

        // out of order
        val (førstegangsbehandlingId2, behandlingOpprettetFørstegang2, sakIdFørstegang2) = behandlingIdOpprettet(Søknad)
        behandlingOpprettetFørstegang2.håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        vedtaksperiodeEndretTilVilkårsprøving(sakIdFørstegang2).håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        vedtaksperiodeEndretTilGodkjenning(sakIdFørstegang2).håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        vedtaksperiodeGodkjent(sakIdFørstegang2).håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        val behandling2 = vedtakFattet(førstegangsbehandlingId2).håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling2.periodetype)

        // revurdering grunnet out of order
        val (revurderingsbehandlingId, behandlingOpprettetRevurdering, sakIdRevurdering) = behandlingIdOpprettet(Søknad, sakId = sakIdFørstegang)
        assertEquals(sakIdRevurdering, sakIdFørstegang)
        behandlingOpprettetRevurdering.håndter(behandlingshendelseDao, revurderingsbehandlingId)
        vedtaksperiodeEndretTilGodkjenning(sakIdRevurdering).håndter(behandlingshendelseDao, revurderingsbehandlingId)
        vedtaksperiodeGodkjent(sakIdRevurdering).håndter(behandlingshendelseDao, revurderingsbehandlingId)
        val behandling3 = vedtakFattet(revurderingsbehandlingId).håndter(behandlingshendelseDao, revurderingsbehandlingId)
        // Dette blir strengt tatt en forlengelse, men vi har i skrivende stund ikke datagrunnlaget til å gjenkjenne et out-of-order-tilfelle
        // Vi forenkler derfor ved å si at en periode som på et eller annet tidligere tidspunkt har vært vilkårsprøvd er en førstegangsbehandling
        // Dette vil være riktig for "stor statistikk" ettersom det er få tilfeller av out-of-order
        assertEquals(FØRSTEGANGSBEHANDLING, behandling3.periodetype)
    }

    @Test
    fun `peridodetype blir førstegangsbehandling ved godkjenning dersom tidligere hendelse på behandlingen er markert som førstegangsbehandling`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        vedtaksperiodeEndretTilVilkårsprøving(sakId).håndter(behandlingshendelseDao, behandlingId)
        val behandling = vedtaksperiodeEndretTilGodkjenning(sakId).håndter(behandlingshendelseDao, behandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
    }

    @Test
    fun `start og slutt for godkjent vedtak`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        vedtaksperiodeEndretTilGodkjenning(sakId).håndter(behandlingshendelseDao, behandlingId)

        var behandling = vedtaksperiodeGodkjent(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingsmetode.MANUELL, behandling.behandlingsmetode)
        assertEquals(VEDTATT, behandling.behandlingsresultat)

        behandling = vedtakFattet(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingsmetode.AUTOMATISK, behandling.behandlingsmetode)
    }

    @Test
    fun `start og slutt for avvist vedtak`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        vedtaksperiodeEndretTilGodkjenning(sakId).håndter(behandlingshendelseDao, behandlingId)

        var behandling = vedtaksperiodeAvvist(sakId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingsmetode.MANUELL, behandling.behandlingsmetode)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertEquals("SB456", behandling.beslutterEnhet)

        behandling = vedtakFattet(behandlingId).håndter(behandlingshendelseDao, behandlingId)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertEquals("SB456", behandling.beslutterEnhet)
    }

    @Test
    fun `presisjon på tidsstempler truncates ned til 6 desimaler i databasen`() {
        val tidspunkt = LocalDateTime.parse("2024-02-13T15:29:54.123123123")
        val (behandlingId, behandlingOpprettet, _) = behandlingIdOpprettet(Søknad, innsendt = tidspunkt, registrert = tidspunkt, opprettet = tidspunkt)
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

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
        val (behandlingId, behandlingOpprettet, _) = behandlingIdOpprettet(Søknad, innsendt = innsendt, registrert = registrert, opprettet = opprettet)
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        fun String.antallDesimaler() = if (this.contains(".")) this.split(".").last().length else 0
        //assertEquals(6, funksjonellTid!!.antallDesimaler())
        assertEquals(6, mottattTid!!.antallDesimaler())
        assertEquals(6, registrertTid!!.antallDesimaler())
    }

    @Test
    fun `start og slutt for auu`() {
        val (behandlingId, behandlingOpprettet) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)
        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = behandlingForkastet(sakId)
        behandling = behandlingForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val (januarBehandlingId, januarBehandlingOpprettet, januarSakId) = behandlingIdOpprettet(Søknad)

        var utbetaltBehandling = januarBehandlingOpprettet.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertNull(utbetaltBehandling.behandlingsresultat)
        assertEquals(utbetaltBehandling.behandlingsmetode, Behandling.Behandlingsmetode.AUTOMATISK)


        val januarVedtakFattet = vedtakFattet(januarBehandlingId)
        utbetaltBehandling = januarVedtakFattet.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.VEDTAK_IVERKSATT, utbetaltBehandling.behandlingsresultat)
        assertEquals(Behandling.Behandlingsmetode.AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        val (annulleringBehandlingId, januarAnnullertBehandlingOpprettet) = behandlingIdOpprettet(TilInfotrygd, januarSakId, avsender = Saksbehandler)
        var annullertBehandling = januarAnnullertBehandlingOpprettet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.SAKSBEHANDLER, annullertBehandling.behandlingskilde)
        assertNull(annullertBehandling.behandlingsresultat)

        val behandlingForkastet = behandlingForkastet(januarSakId)
        annullertBehandling = behandlingForkastet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        utbetaltBehandling = behandlingshendelseDao.hent(januarBehandlingId)!!

        assertEquals(3, januarBehandlingId.rader) // Registret, Vedtatt, Avbrutt
        assertEquals(2, annulleringBehandlingId.rader) // Registret, Avbrutt

        listOf(annullertBehandling, utbetaltBehandling).forEach {
            assertEquals(AVSLUTTET, it.behandlingstatus)
            assertEquals(Behandling.Behandlingstype.SØKNAD, it.behandlingstype)
            assertEquals(AVBRUTT, it.behandlingsresultat)
        }

        assertEquals(Behandling.Behandlingsmetode.MANUELL, utbetaltBehandling.behandlingsmetode)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(TilInfotrygd)
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = behandlingForkastet(sakId)
        behandling = behandlingForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `en omgjøring av auu`() {
        val (behandlingId, behandlingOpprettet, sakId) = behandlingIdOpprettet(Søknad)

        val avsluttetUtenVedtak = avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, behandlingOpprettet2) = behandlingIdOpprettet(Omgjøring, sakId, avsender = Arbeidsgiver)
        val behandling2 = behandlingOpprettet2.håndter(behandlingshendelseDao, behandlingId2)

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

       internal val Sykmeldt = BehandlingOpprettet.Avsender("SYKMELDT")
       internal val Arbeidsgiver = BehandlingOpprettet.Avsender("ARBEIDSGIVER")
       internal val Saksbehandler = BehandlingOpprettet.Avsender("SAKSBEHANDLER")
       internal val System = BehandlingOpprettet.Avsender("SYSTEM")

       internal val Søknad = BehandlingOpprettet.Behandlingstype("Søknad")
       internal val TilInfotrygd = BehandlingOpprettet.Behandlingstype("TilInfotrygd")
       internal val Omgjøring = BehandlingOpprettet.Behandlingstype("Omgjøring")
       internal val Revurdering = BehandlingOpprettet.Behandlingstype("Revurdering")


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

       internal fun behandlingIdOpprettet(
           behandlingstype: BehandlingOpprettet.Behandlingstype,
           sakId: SakId = SakId(UUID.randomUUID()),
           behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()),
           aktørId: String = "1234",
           avsender: BehandlingOpprettet.Avsender = Sykmeldt,
           innsendt: LocalDateTime = nesteTidspunkt,
           registrert: LocalDateTime = nesteTidspunkt,
           opprettet: LocalDateTime = nesteTidspunkt,
       ): Triple<BehandlingId, BehandlingOpprettet, SakId> {
           val behandlingskilde = BehandlingOpprettet.Behandlingskilde(innsendt, registrert, avsender)
           val behandlingOpprettet = BehandlingOpprettet(UUID.randomUUID(), opprettet, blob, sakId.id, behandlingId.id, aktørId, behandlingskilde, behandlingstype)
           return Triple(behandlingId, behandlingOpprettet, sakId)
       }
       internal fun vedtakFattet(behandlingId: BehandlingId) = VedtakFattet(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun avsluttetUtenVedtak(behandlingId: BehandlingId) = AvsluttetUtenVedtak(UUID.randomUUID(), nesteTidspunkt, blob, behandlingId.id)
       internal fun behandlingForkastet(sakId: SakId, behandlingsmetode: Behandling.Behandlingsmetode = Behandling.Behandlingsmetode.MANUELL) = BehandlingForkastet(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id, behandlingsmetode)
       internal fun vedtaksperiodeEndretTilGodkjenning(sakId: SakId) = VedtaksperiodeEndretTilGodkjenning(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id)
       internal fun vedtaksperiodeEndretTilVilkårsprøving(sakId: SakId) = VedtaksperiodeEndretTilVilkårsprøving(UUID.randomUUID(), nesteTidspunkt, blob, sakId.id)
       internal fun vedtaksperiodeGodkjent(sakId: SakId) = VedtaksperiodeBeslutning(
           id = UUID.randomUUID(),
           opprettet = nesteTidspunkt,
           data = blob,
           vedtaksperiodeId = sakId.id,
           saksbehandlerEnhet = "SB123",
           beslutterEnhet = "SB456",
           automatiskBehandling = false,
           behandlingsresultat = VEDTATT,
           eventName = "vedtaksperiode_godkjent",
           behandlingstatus = Behandling.Behandlingstatus.GODKJENT
       )
       internal fun vedtaksperiodeAvvist(sakId: SakId) = VedtaksperiodeBeslutning(
           id = UUID.randomUUID(),
           opprettet = nesteTidspunkt,
           data = blob,
           vedtaksperiodeId = sakId.id,
           saksbehandlerEnhet = "SB123",
           beslutterEnhet = "SB456",
           automatiskBehandling = false,
           behandlingsresultat = AVBRUTT,
           eventName = "vedtaksperiode_avvist",
           behandlingstatus = AVSLUTTET
       )
   }
}