package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*

internal class UventedeHendelserTest : AbstractTeamSakTest() {

    @Test
    fun `feiler når vi bygger videre på en avsluttet behandling`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        val (_, hendelsefabrikk) = nyttVedtak(behandlingId = behandlingId)
        val godkjenthendelse = hendelsefabrikk.vedtaksperiodeGodkjent()
        assertThrows<IllegalStateException> {
            godkjenthendelse.håndter(behandlingId)
        }
        assertThrows<IllegalStateException> {
            godkjenthendelse.håndter(behandlingId)
        }
    }

    @Test
    fun `ignorerer hendelse vi har håndtert tidligere`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(behandlingId = behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)
        val vedtaksperiodeGodkjent = hendelsefabrikk.vedtaksperiodeGodkjent(totrinnsbehandling = false)
        vedtaksperiodeGodkjent.håndter(behandlingId)

        var behandling = hendelsefabrikk.vedtakFattet(tags = setOf(Tag.Arbeidsgiverutbetaling, Tag.Innvilget, Tag.Førstegangsbehandling)).håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)

        behandling = vedtaksperiodeGodkjent.håndter(behandlingId)
        assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
    }
}