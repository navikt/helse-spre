package db.migration

import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.BehandlingId
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.*

internal class V32BehandlingsstatusVedtaksperiodeGodkjentTest: BehandlingshendelseJsonMigreringTest(
    migrering = V32__behandlingsstatus_vedtaksperiode_godkjent()
) {
    @Test
    fun `skal skrive om alle vedtaksperiode_godkjent-hendelser sin behandlingsstatus fra AVSLUTTET til GODKJENT`() {
        val behandlingId = UUID.randomUUID()
        val hendelsefabrikk = Hendelsefabrikk(behandlingId = BehandlingId(behandlingId))
        val korrigertHendelse = leggTilBehandlingshendelse(
            sakId = UUID.randomUUID(),
            behandlingId = behandlingId,
            siste = true,
            versjon = Versjon.of("0.1.0"),
            erKorrigert = false, data = {
                it.put("behandlingstatus", "AVSLUTTET")
            },
            hendelse = hendelsefabrikk.vedtaksperiodeGodkjent()
        )

        migrer()
        assertKorrigert(korrigertHendelse) { _, ny ->
            Assertions.assertEquals("GODKJENT", ny.path("behandlingstatus").asText())
        }
    }

}