package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.Revurdering
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.nyBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.REVURDERING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.SØKNAD
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Periodetype.FØRSTEGANGSBEHANDLING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RevurderingTest: AbstractTeamSakTest() {


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
        assertEquals(SØKNAD, behandling.behandlingstype)

        val (revurderingbehandlingId, behandlingOpprettetRevurdering) = hendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId(), behandlingstype = Revurdering)
        behandlingOpprettetRevurdering.håndter(revurderingbehandlingId)
        val behandlingRevurdering = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(revurderingbehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandlingRevurdering.behandlingstatus)
        assertEquals(FØRSTEGANGSBEHANDLING, behandlingRevurdering.periodetype)
        assertEquals(REVURDERING, behandlingRevurdering.behandlingstype)
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
        assertEquals(SØKNAD, behandling.behandlingstype)

        // out of order
        val outOfOrderHendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId2, behandlingOpprettetFørstegang2) = outOfOrderHendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang2.håndter(førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeEndretTilVilkårsprøving().håndter(førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(førstegangsbehandlingId2)
        outOfOrderHendelsefabrikk.vedtaksperiodeGodkjent().håndter(førstegangsbehandlingId2)
        val behandling2 = outOfOrderHendelsefabrikk.vedtakFattet().håndter(førstegangsbehandlingId2)
        assertEquals(FØRSTEGANGSBEHANDLING, behandling2.periodetype)
        assertEquals(SØKNAD, behandling2.behandlingstype)

        // revurdering grunnet out of order
        val (revurderingsbehandlingId, behandlingOpprettetRevurdering, sakIdRevurdering) = førstegangsHendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId(), behandlingstype = Revurdering)
        assertEquals(sakIdRevurdering, sakIdFørstegang)
        behandlingOpprettetRevurdering.håndter(revurderingsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(revurderingsbehandlingId)
        førstegangsHendelsefabrikk.vedtaksperiodeGodkjent(behandlingId = revurderingsbehandlingId).håndter(revurderingsbehandlingId)
        val behandling3 = førstegangsHendelsefabrikk.vedtakFattet(revurderingsbehandlingId).håndter(revurderingsbehandlingId)
        // Dette blir strengt tatt en forlengelse, men vi har i skrivende stund ikke datagrunnlaget til å gjenkjenne et out-of-order-tilfelle
        // Vi forenkler derfor ved å si at en periode som på et eller annet tidligere tidspunkt har vært vilkårsprøvd er en førstegangsbehandling
        // Dette vil være riktig for "stor statistikk" ettersom det er få tilfeller av out-of-order
        assertEquals(FØRSTEGANGSBEHANDLING, behandling3.periodetype)
        assertEquals(REVURDERING, behandling3.behandlingstype)
    }

}