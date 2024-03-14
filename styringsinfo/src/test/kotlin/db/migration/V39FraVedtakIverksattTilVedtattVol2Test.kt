package db.migration

import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V39FraVedtakIverksattTilVedtattVol2Test: BehandlingshendelseJsonMigreringTest(
    migrering = V39__fra_vedtak_iverksatt_til_vedtatt_vol2(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om VEDTAK_IVERKSATT til VEDTATT`() {
        val behandlingId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val behandling = leggTilBehandlingshendelse(
            sakId, behandlingId, true, Versjon.of("0.1.0"), false, data = {
                it.put("behandlingsresultat", "VEDTAK_IVERKSATT")
            }
        )

        migrer()
        assertKorrigert(behandling) { _, ny ->
            assertEquals(ny.path("behandlingsresultat").asText(), "VEDTATT")
        }
    }

}