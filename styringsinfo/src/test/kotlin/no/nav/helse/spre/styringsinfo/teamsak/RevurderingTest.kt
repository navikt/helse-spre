package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.Revurdering
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.nyBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.REVURDERING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.SØKNAD
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RevurderingTest: AbstractTeamSakTest() {


    @Test
    fun `førstegangsbehandling revurderes`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (førstegangsbehandlingId, behandlingOpprettetFørstegang) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettetFørstegang.håndter(førstegangsbehandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(førstegangsbehandlingId)
        hendelsefabrikk.vedtaksperiodeGodkjent().håndter(førstegangsbehandlingId)
        val behandling = hendelsefabrikk.vedtakFattet().håndter(førstegangsbehandlingId)

        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(SØKNAD, behandling.behandlingstype)

        val (revurderingbehandlingId, behandlingOpprettetRevurdering) = hendelsefabrikk.behandlingOpprettet(behandlingId = nyBehandlingId(), behandlingstype = Revurdering)
        behandlingOpprettetRevurdering.håndter(revurderingbehandlingId)
        val behandlingRevurdering = hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(revurderingbehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandlingRevurdering.behandlingstatus)
        assertEquals(REVURDERING, behandlingRevurdering.behandlingstype)
    }
}