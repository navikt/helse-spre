package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.SakId
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.lang.IllegalStateException
import java.util.UUID

internal class UventedeHendelserTest : AbstractTeamSakTest() {

    @Test
    fun `feiler når vi bygger videre på en avsluttet behandling`() {
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())

        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)
        val vedtaksperiodeGodkjent = hendelsefabrikk.vedtaksperiodeGodkjent(totrinnsbehandling = false)
        vedtaksperiodeGodkjent.håndter(behandlingId)

        val behandling = hendelsefabrikk.vedtakFattet(tags = listOf(VedtakFattet.Companion.Tag.Arbeidsgiverutbetaling, VedtakFattet.Companion.Tag.Innvilget)).håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)

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
        val sakId = SakId(UUID.randomUUID())
        val behandlingId = BehandlingId(UUID.randomUUID())

        val hendelsefabrikk = Hendelsefabrikk(sakId, behandlingId)
        val (_, behandlingOpprettet) = hendelsefabrikk.behandlingOpprettet()
        behandlingOpprettet.håndter(behandlingId)

        hendelsefabrikk.vedtaksperiodeEndretTilGodkjenning().håndter(behandlingId)
        val vedtaksperiodeGodkjent = hendelsefabrikk.vedtaksperiodeGodkjent(totrinnsbehandling = false)
        vedtaksperiodeGodkjent.håndter(behandlingId)

        var behandling = hendelsefabrikk.vedtakFattet(tags = listOf(VedtakFattet.Companion.Tag.Arbeidsgiverutbetaling, VedtakFattet.Companion.Tag.Innvilget)).håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)

        behandling = vedtaksperiodeGodkjent.håndter(behandlingId)
        Assertions.assertEquals(Behandling.Behandlingstatus.AVSLUTTET, behandling.behandlingstatus)
    }
}