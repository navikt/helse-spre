package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingstatus.AVSLUTTET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.Tag
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class UventedeHendelserTest : AbstractTeamSakTest() {

    @Test
    fun `ignorerer hendelse vi har håndtert tidligere`() {
        val behandlingId = BehandlingId(UUID.randomUUID())
        val hendelsefabrikk = Hendelsefabrikk(behandlingId = behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.utkastTilVedtak().håndter(behandlingId)
        val vedtaksperiodeGodkjent = hendelsefabrikk.vedtaksperiodeGodkjent(totrinnsbehandling = false)
        vedtaksperiodeGodkjent.håndter(behandlingId)

        var behandling = hendelsefabrikk.vedtakFattet(tags = setOf(Tag.Arbeidsgiverutbetaling, Tag.Innvilget, Tag.Førstegangsbehandling)).håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)

        behandling = vedtaksperiodeGodkjent.håndter(behandlingId)
        assertEquals(AVSLUTTET, behandling.behandlingstatus)
    }
}