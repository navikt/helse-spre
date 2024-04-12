package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.AVBRUTT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ManueltBehandletAvSaksbehandlerTest: AbstractTeamSakTest() {

    @Test
    fun `start og slutt for utkast til vedtak som avvises av saksbehandler`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.utkastTilVedtak().håndter(behandlingId)

        val behandling = hendelsefabrikk.vedtaksperiodeAvvist().håndter(behandlingId)
        assertEquals(MANUELL, behandling.behandlingsmetode)
        assertEquals(AVBRUTT, behandling.behandlingsresultat)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals("SB123", behandling.saksbehandlerEnhet)
        assertNull(behandling.beslutterEnhet)
    }

    @Test
    fun `hendelses- og behandlingsmetode for godkjent vedtak av saksbehandler`() {
        val (behandling, _) = nyttVedtak()
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
        assertEquals(MANUELL, behandling.behandlingsmetode)
    }


    @Test
    fun `når to saksbehandlere har behandlet saken blir det behandlingsmetode totrinns`() {
        val (behandling, _) = nyttVedtak(totrinnsbehandling = true)
        assertEquals(TOTRINNS, behandling.behandlingsmetode)
        assertEquals(AUTOMATISK, behandling.hendelsesmetode)
    }
}