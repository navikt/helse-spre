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
        assertEquals(Behandling.Behandlingsresultat.IKKE_REALITETSBEHANDLET, behandling.behandlingsresultat)
    }

    @Test
    fun `når to saksbehandlere har behandlet saken blir det behandlingsmetode totrinns`() {
        val behandling = nyttVedtak(totrinnsbehandling = true)
        assertEquals(TOTRINNS, behandling.behandlingsmetode)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
    }
}