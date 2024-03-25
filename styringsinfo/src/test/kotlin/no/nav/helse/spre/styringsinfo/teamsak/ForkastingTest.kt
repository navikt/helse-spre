package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ForkastingTest: AbstractTeamSakTest() {

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
}