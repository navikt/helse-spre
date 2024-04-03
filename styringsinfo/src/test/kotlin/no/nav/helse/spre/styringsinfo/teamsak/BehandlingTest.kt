package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.IKKE_REALITETSBEHANDLET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class BehandlingTest: AbstractTeamSakTest() {

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
        nyttVedtak()
    }

    @Test
    fun `start og slutt for auu`() {
        val hendelsefabrikk = Hendelsefabrikk()
        val (behandlingId, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        assertUkjentBehandling(behandlingId)
        var behandling = behandlingOpprettet.håndter(behandlingId)
        assertEquals(REGISTRERT, behandling.behandlingstatus)
        assertNull(behandling.behandlingsresultat)

        val avsluttetUtenVedtak = hendelsefabrikk.avsluttetUtenVedtak()
        behandling = avsluttetUtenVedtak.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
        assertEquals(IKKE_REALITETSBEHANDLET, behandling.behandlingsresultat)
    }
}