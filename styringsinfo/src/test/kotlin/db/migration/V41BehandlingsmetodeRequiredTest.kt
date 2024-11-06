package db.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V41BehandlingsmetodeRequiredTest: BehandlingshendelseJsonMigreringTest(
    migrering = V41__behandlingsmetode_required()
) {

    @Test
    fun `korrigerer inn behandlingsmetode AUTOMATISK der hvor det mangler`() {
        val radUtenBehandlingsmetode1 = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.putNull("behandlingsmetode")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsmetode", "MANUELL")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsmetode", "AUTOMATISK")
        }

        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsmetode", "TOTRINNS")
        }

        val radUtenBehandlingsmetode2 = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID())

        migrer()

        assertKorrigert(radUtenBehandlingsmetode1) { _, ny ->
            assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
        }
        assertKorrigert(radUtenBehandlingsmetode2) { _, ny ->
            assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
        }
    }
}