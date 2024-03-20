package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.nyBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FORLENGELSE
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FØRSTEGANGSBEHANDLING
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet.Companion.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class TeamSakTest: AbstractTeamSakTest() {

    @Test
    fun `funksjonell lik behandling`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()

        assertEquals(0, behandlingId.rader)
        behandlingOpprettet.håndter(behandlingId)
        assertEquals(1, behandlingId.rader)

        val vedtakFattet = hendelsefabrikk.vedtakFattet()
        vedtakFattet.håndter(behandlingId)
        assertEquals(2, behandlingId.rader)

        vedtakFattet.håndter(behandlingId)
        assertEquals(2, behandlingId.rader)
    }

    @Test
    fun `start og slutt for vedtak`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)
        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)

        behandling = hendelsefabrikk.vedtakFattet(tags = listOf(Tag.Arbeidsgiverutbetaling, Tag.Innvilget)).håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Mottaker.ARBEIDSGIVER, behandling.mottaker)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
    }

    @Test
    fun `periodetype blir førstegangsbehandling for perioder som vilkårsprøves`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)

        val behandling = hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingId)
        assertEquals(VURDERER_INNGANGSVILKÅR, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
    }

    @Test
    fun `førstegangsbehandling revurderes`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang.håndter(førstegangsbehandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(førstegangsbehandlingId)
        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(førstegangsbehandlingId)
        hendelsefabrikk.vedtaksperiodeGodkjent().håndter(førstegangsbehandlingId)
        val behandling = hendelsefabrikk.vedtakFattet().håndter(førstegangsbehandlingId)

        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)

        val (revurderingbehandlingId, behandlingOpprettetRevurdering) = hendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId())
        behandlingOpprettetRevurdering.håndter(revurderingbehandlingId)
        val behandlingRevurdering = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(revurderingbehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandlingRevurdering.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandlingRevurdering.periodetype)
    }

    @Test
    fun `peridodetype blir forlengelse ved godkjenning dersom ingen tidligere hendelse på behandlingen er markert som førstegangsbehandling`() {
        val hendelsefabrikkFørstegangs = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang) = hendelsefabrikkFørstegangs.behandlingOpprettet()

        behandlingOpprettetFørstegang.håndter(førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtaksperiodeEndretTilVilkårsprøving().håndter(førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtaksperiodeEndretTilGodkjenning().håndter(førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtaksperiodeGodkjent().håndter(førstegangsbehandlingId)
        hendelsefabrikkFørstegangs.vedtakFattet().håndter(førstegangsbehandlingId)

        val hendelsefabrikkForlengelse = Hendelsefabrikk()

        val (forlengelseBehandlingId, behandlingOpprettetForlengelse) = hendelsefabrikkForlengelse.behandlingOpprettet(behandlingId = nyBehandlingId())
        behandlingOpprettetForlengelse.håndter(forlengelseBehandlingId)
        val behandling = hendelsefabrikkForlengelse.vedtaksperiodeEndretTilGodkjenning().håndter(forlengelseBehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FORLENGELSE, behandling.periodetype)
    }

    @Test
    fun `periodetype går fra førstegangsbehandling til forlengelse ved et snasent out-of-order-scenario`() {
        val førstegangsHendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang, sakIdFørstegang) = førstegangsHendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang.håndter(førstegangsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(førstegangsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(førstegangsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeGodkjent().håndter(førstegangsbehandlingId)
        val behandling = førstegangsHendelsefabrikk.vedtakFattet().håndter(førstegangsbehandlingId)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)

        // out of order
        val outOfOrderHendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId2, behandlingOpprettetFørstegang2) = outOfOrderHendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang2.håndter(førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeGodkjent().håndter(førstegangsbehandlingId2)
        val behandling2 = outOfOrderHendelsefabrikk.vedtakFattet().håndter(førstegangsbehandlingId2)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling2.periodetype)

        // revurdering grunnet out of order
        val (revurderingsbehandlingId, behandlingOpprettetRevurdering, sakIdRevurdering) = førstegangsHendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId())
        assertEquals(sakIdRevurdering, sakIdFørstegang)
        behandlingOpprettetRevurdering.håndter(revurderingsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(revurderingsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeGodkjent().håndter(revurderingsbehandlingId)
        val behandling3 = førstegangsHendelsefabrikk.vedtakFattet(revurderingsbehandlingId).håndter(revurderingsbehandlingId)
        // Dette blir strengt tatt en forlengelse, men vi har i skrivende stund ikke datagrunnlaget til å gjenkjenne et out-of-order-tilfelle
        // Vi forenkler derfor ved å si at en periode som på et eller annet tidligere tidspunkt har vært vilkårsprøvd er en førstegangsbehandling
        // Dette vil være riktig for "stor statistikk" ettersom det er få tilfeller av out-of-order
        assertEquals(FØRSTEGANGSBEHANDLING, behandling3.periodetype)
    }

    @Test
    fun `peridodetype blir førstegangsbehandling ved godkjenning dersom tidligere hendelse på behandlingen er markert som førstegangsbehandling`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)
        hendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(behandlingId)
        val behandling = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandling.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling.periodetype)
    }

    @Test
    fun `start og slutt for godkjent vedtak`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)

        var behandling = hendelsefabrikk.vedtaksperiodeGodkjent().håndter(behandlingId)
        assertEquals(MANUELL, behandling.behandlingsmetode)
        assertNull(behandling.behandlingsresultat)

        behandling = hendelsefabrikk.vedtakFattet().håndter(behandlingId)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
        assertEquals(MANUELL, behandling.behandlingsmetode)
        assertEquals(Behandling.Behandlingsresultat.INNVILGET, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for utkast til vedtak som avvises av saksbehandler i speil`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)

        val behandling = hendelsefabrikk.vedtaksperiodeAvvist().håndter(behandlingId)
        assertEquals(MANUELL, behandling.behandlingsmetode)
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
        behandlingOpprettet.håndter(behandlingId)

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
        behandlingOpprettet.håndter(behandlingId)

        fun String.antallDesimaler() = if (this.contains(".")) this.split(".").last().length else 0
        //assertEquals(6, funksjonellTid!!.antallDesimaler())
        assertEquals(6, mottattTid!!.antallDesimaler())
        assertEquals(6, registrertTid!!.antallDesimaler())
    }

    @Test
    fun `start og slutt for auu`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = hendelsefabrikk.avsluttetUtenVedtak()
        behandling = avsluttetUtenVedtak.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingsresultat.HENLAGT, behandling.behandlingsresultat)
    }

    @Test
    fun `start og slutt for forkastet periode`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet()
        behandling = behandlingForkastet.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.REGISTRERT, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet()
        behandling = behandlingForkastet.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(Behandling.Behandlingstype.SØKNAD, behandling.behandlingstype)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
    }

    @Test
    fun `når to saksbehandlere har behandlet saken blir det behandlingsmetode totrinns`() {
        val behandling = nyttVedtak(totrinnsbehandling = true)
        assertEquals(TOTRINNS, behandling.behandlingsmetode)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
    }
}