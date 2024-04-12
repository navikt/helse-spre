package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.Revurdering
import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk.Companion.nyBehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVVENTER_GODKJENNING
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.REVURDERING
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class RevurderingTest: AbstractTeamSakTest() {


    @Test
    fun `førstegangsbehandling revurderes`() {
        val (_, hendelsefabrikk) = nyttVedtak()

        val (revurderingbehandlingId, behandlingOpprettetRevurdering) = hendelsefabrikk.behandlingOpprettet(
            behandlingId = nyBehandlingId(),
            behandlingstype = Revurdering)
        behandlingOpprettetRevurdering.håndter(revurderingbehandlingId)
        val behandlingRevurdering = hendelsefabrikk.utkastTilVedtak(behandlingId = revurderingbehandlingId).håndter(revurderingbehandlingId)

        assertEquals(AVVENTER_GODKJENNING, behandlingRevurdering.behandlingstatus)
        assertEquals(REVURDERING, behandlingRevurdering.behandlingstype)
    }
}