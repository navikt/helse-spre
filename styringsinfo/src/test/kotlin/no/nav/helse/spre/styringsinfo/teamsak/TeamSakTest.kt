package no.nav.helse.spre.styringsinfo.teamsak

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.Arbeidsgiver
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.Saksbehandler
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.TilInfotrygd
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.nyBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.TOTRINNS
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FORLENGELSE
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.PostgresBehandlingshendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Hendelse
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.HendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.PostgresHendelseDao
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag
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
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()

        assertEquals(0, behandlingId.rader)
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(1, behandlingId.rader)

        val vedtakFattet = hendelsefabrikk.vedtakFattet()
        vedtakFattet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(2, behandlingId.rader)

        vedtakFattet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtakFattet(tags = listOf(Tag.Arbeidsgiverutbetaling, Tag.Innvilget)).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Mottaker.ARBEIDSGIVER, behandling.mottaker)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
    }

    @Test
    fun `periodetype blir førstegangsbehandling for perioder som vilkårsprøves`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        val behandling = hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(VURDERER_INNGANGSVILKÅR, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
    }

    @Test
    fun `førstegangsbehandling revurderes`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang.håndter(behandlingshendelseDao, førstegangsbehandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        hendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        val behandling = hendelsefabrikk.vedtakFattet().håndter(behandlingshendelseDao, førstegangsbehandlingId)

        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)

        val (revurderingbehandlingId, behandlingOpprettetRevurdering) = hendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId())
        behandlingOpprettetRevurdering.håndter(behandlingshendelseDao, revurderingbehandlingId)
        val behandlingRevurdering = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, revurderingbehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandlingRevurdering.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandlingRevurdering.periodetype)
    }

    @Test
    fun `peridodetype blir forlengelse ved godkjenning dersom ingen tidligere hendelse på behandlingen er markert som førstegangsbehandling`() {
        val hendelsefabrikkFørstegangs = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang) = hendelsefabrikkFørstegangs.behandlingOpprettet()

        behandlingOpprettetFørstegang.håndter(behandlingshendelseDao, førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtaksperiodeGodkjent().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtakFattet().håndter(behandlingshendelseDao, førstegangsbehandlingId)

        val hendelsefabrikkForlengelse = Hendelsefabrikk()

        val (forlengelseBehandlingId, behandlingOpprettetForlengelse) = hendelsefabrikkForlengelse.behandlingOpprettet(behandlingId = nyBehandlingId())
        behandlingOpprettetForlengelse.håndter(behandlingshendelseDao, forlengelseBehandlingId)
        val behandling = hendelsefabrikkForlengelse.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, forlengelseBehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FORLENGELSE, behandling.periodetype)
    }

    @Test
    fun `periodetype går fra førstegangsbehandling til forlengelse ved et snasent out-of-order-scenario`() {
        val førstegangsHendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang, sakIdFørstegang) = førstegangsHendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang.håndter(behandlingshendelseDao, førstegangsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        val behandling = førstegangsHendelsefabrikk.vedtakFattet().håndter(behandlingshendelseDao, førstegangsbehandlingId)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)

        // out of order
        val outOfOrderHendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId2, behandlingOpprettetFørstegang2) = outOfOrderHendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang2.håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        val behandling2 = outOfOrderHendelsefabrikk.vedtakFattet().håndter(behandlingshendelseDao, førstegangsbehandlingId2)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling2.periodetype)

        // revurdering grunnet out of order
        val (revurderingsbehandlingId, behandlingOpprettetRevurdering, sakIdRevurdering) = førstegangsHendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId())
        assertEquals(sakIdRevurdering, sakIdFørstegang)
        behandlingOpprettetRevurdering.håndter(behandlingshendelseDao, revurderingsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, revurderingsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingshendelseDao, revurderingsbehandlingId)
        val behandling3 = førstegangsHendelsefabrikk.vedtakFattet(revurderingsbehandlingId).håndter(behandlingshendelseDao, revurderingsbehandlingId)
        // Dette blir strengt tatt en forlengelse, men vi har i skrivende stund ikke datagrunnlaget til å gjenkjenne et out-of-order-tilfelle
        // Vi forenkler derfor ved å si at en periode som på et eller annet tidligere tidspunkt har vært vilkårsprøvd er en førstegangsbehandling
        // Dette vil være riktig for "stor statistikk" ettersom det er få tilfeller av out-of-order
        assertEquals(FØRSTEGANGSBEHANDLING, behandling3.periodetype)
    }

    @Test
    fun `peridodetype blir førstegangsbehandling ved godkjenning dersom tidligere hendelse på behandlingen er markert som førstegangsbehandling`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, behandlingId)
        val behandling = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, behandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
    }

    @Test
    fun `start og slutt for godkjent vedtak`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, behandlingId)

        var behandling = hendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Metode.MANUELL, behandling.behandlingsmetode)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtakFattet().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
        assertEquals(Behandling.Metode.MANUELL, behandling.behandlingsmetode)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for utkast til vedtak som avvises av saksbehandler i speil`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, behandlingId)

        val behandling = hendelsefabrikk.vedtaksperiodeAvvist().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Metode.MANUELL, behandling.behandlingsmetode)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertNull(behandling.beslutterEnhet)
    }

    @Test
    fun `presisjon på tidsstempler truncates ned til 6 desimaler i databasen`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val tidspunkt = LocalDateTime.parse("2024-02-13T15:29:54.123123123")
        val (behandlingId, behandlingOpprettet, _) = hendelsefabrikk.behandlingOpprettet(innsendt = tidspunkt, registrert = tidspunkt, opprettet = tidspunkt)
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        fun String.antallDesimaler() = if (this.contains(".")) this.split(".").last().length else 0
        assertEquals(6, funksjonellTid!!.antallDesimaler())
        assertEquals(6, mottattTid!!.antallDesimaler())
        assertEquals(6, registrertTid!!.antallDesimaler())
    }

    @Test
    fun `presisjon på tidsstempler justeres opp til 6 desimaler i databasen`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val innsendt = LocalDateTime.parse("2024-02-13T15:29")
        val registrert = LocalDateTime.parse("2024-02-20T15:29")
        val opprettet = LocalDateTime.parse("2024-02-20T15:29:54.123")
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(innsendt = innsendt, registrert = registrert, opprettet = opprettet)
        behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)

        fun String.antallDesimaler() = if (this.contains(".")) this.split(".").last().length else 0
        //assertEquals(6, funksjonellTid!!.antallDesimaler())
        assertEquals(6, mottattTid!!.antallDesimaler())
        assertEquals(6, registrertTid!!.antallDesimaler())
    }

    @Test
    fun `start og slutt for auu`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = hendelsefabrikk.avsluttetUtenVedtak()
        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet()
        behandling = behandlingForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `annullering av en tidligere utbetalt periode`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (januarBehandlingId, januarBehandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()

        var utbetaltBehandling = januarBehandlingOpprettet.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertNull(utbetaltBehandling.behandlingsresultat)
        assertEquals(utbetaltBehandling.behandlingsmetode, AUTOMATISK)


        val januarVedtakFattet = hendelsefabrikk.vedtakFattet()
        utbetaltBehandling = januarVedtakFattet.håndter(behandlingshendelseDao, januarBehandlingId)
        assertEquals(AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, utbetaltBehandling.behandlingsresultat)
        assertEquals(AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        val (annulleringBehandlingId, januarAnnullertBehandlingOpprettet) = hendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId(), behandlingstype = TilInfotrygd, avsender = Saksbehandler)
        var annullertBehandling = januarAnnullertBehandlingOpprettet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, annullertBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.SAKSBEHANDLER, annullertBehandling.behandlingskilde)
        assertNull(annullertBehandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet(annulleringBehandlingId)
        annullertBehandling = behandlingForkastet.håndter(behandlingshendelseDao, annulleringBehandlingId)
        utbetaltBehandling = behandlingshendelseDao.hent(januarBehandlingId)!!

        assertEquals(2, januarBehandlingId.rader) // Registrert, Vedtatt
        assertEquals(2, annulleringBehandlingId.rader) // Registrert, Avbrutt

        assertEquals(AVSLUTTET, utbetaltBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, utbetaltBehandling.behandlingstype)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, utbetaltBehandling.behandlingsresultat)
        assertEquals(AUTOMATISK, utbetaltBehandling.behandlingsmetode)

        assertEquals(AVSLUTTET, annullertBehandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, annullertBehandling.behandlingstype)
        assertEquals(AVBRUTT, annullertBehandling.behandlingsresultat)
        assertEquals(Behandling.Metode.MANUELL, annullertBehandling.behandlingsmetode)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet()
        behandling = behandlingForkastet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `en omgjøring av auu`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()

        val avsluttetUtenVedtak = hendelsefabrikk.avsluttetUtenVedtak(behandlingId)

        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.relatertBehandlingId)

        behandling = avsluttetUtenVedtak.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
        assertNull(behandling.relatertBehandlingId)

        val (behandlingId2, behandlingOpprettet2) = hendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId(), avsender = Arbeidsgiver, behandlingstype = Hendelsefabrikk.Omgjøring)
        val behandling2 = behandlingOpprettet2.håndter(behandlingshendelseDao, behandlingId2)

        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling2.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.OMGJØRING, behandling2.behandlingstype)
        assertEquals(Behandling.Behandlingskilde.ARBEIDSGIVER, behandling2.behandlingskilde)
        assertEquals(behandlingId, behandling2.relatertBehandlingId)
    }

    @Test
    fun `når to saksbehandlere har behandlet saken blir det behandlingsmetode totrinns`() {
        val behandling = nyttVedtak(totrinnsbehandling = true)
        assertEquals(TOTRINNS, behandling.behandlingsmetode)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
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

    private fun nyttVedtak(sakId: SakId = SakId(UUID.randomUUID()), behandlingId: BehandlingId = BehandlingId(UUID.randomUUID()), totrinnsbehandling: Boolean = false): Behandling {
        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertNull(behandlingshendelseDao.hent(behandlingId))
        var behandling = behandlingOpprettet.håndter(behandlingshendelseDao, behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(VURDERER_INNGANGSVILKÅR, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtaksperiodeGodkjent(totrinnsbehandling = totrinnsbehandling).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(GODKJENT, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtakFattet(tags = listOf(Tag.Arbeidsgiverutbetaling, Tag.Innvilget)).håndter(behandlingshendelseDao, behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Mottaker.ARBEIDSGIVER, behandling.mottaker)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
        return behandling
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
        private val objectMapper = jacksonObjectMapper()
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
   }
}