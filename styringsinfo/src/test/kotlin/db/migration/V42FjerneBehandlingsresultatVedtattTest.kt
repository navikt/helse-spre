package db.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V42FjerneBehandlingsresultatVedtattTest: BehandlingshendelseJsonMigreringTest(
    migrering = V42__fjerne_behandlingsresultat_vedtatt()
) {

    @Test
    fun `setter behandlingsresultat INNVILGET på de som er siste rad på en behandling, ikke AVSLUTTET og har behandlingsresultat VEDTATT`() {
        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.putNull("behandlingsresultat")
            data.put("behandlingstatus", "REGISTRERT")

        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsresultat", "INNVILGET")
            data.put("behandlingstatus", "AVSLUTTET")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsresultat", "AVSLAG")
            data.put("behandlingstatus", "AVSLUTTET")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsresultat", "VEDTATT")
            data.put("behandlingstatus", "AVSLUTTET")
        }

        val vedtattRad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsresultat", "VEDTATT")
            data.put("behandlingstatus", "AVVENTER_GODKJENNING")
        }

        migrer()

        assertKorrigert(vedtattRad) { gammel, ny ->
            assertEquals("VEDTATT", gammel.path("behandlingsresultat").asText())
            assertEquals("INNVILGET", ny.path("behandlingsresultat").asText())
        }
    }
}