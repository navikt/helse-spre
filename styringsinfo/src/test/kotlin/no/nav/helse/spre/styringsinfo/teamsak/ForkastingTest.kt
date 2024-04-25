package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.REGISTRERT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstype.SØKNAD
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.AUTOMATISK
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.MANUELL
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
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet()
        behandling = behandlingForkastet.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals(MANUELL, behandling.hendelsesmetode)
        assertEquals(AUTOMATISK, behandling.behandlingsmetode)
    }

    @Test
    fun `periode som blir forkastet på direkten`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        assertEquals(SØKNAD, behandling.behandlingstype)
        assertNull(behandling.behandlingsresultat)

        val behandlingForkastet = hendelsefabrikk.behandlingForkastet(hendelsesmetode = AUTOMATISK)
        behandling = behandlingForkastet.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(SØKNAD, behandling.behandlingstype)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
        assertEquals(AUTOMATISK, behandling.behandlingsmetode)
    }
}