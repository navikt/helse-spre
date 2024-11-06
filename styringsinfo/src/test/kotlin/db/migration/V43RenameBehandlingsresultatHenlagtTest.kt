package db.migration

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.UUID

internal class V43RenameBehandlingsresultatHenlagtTest: BehandlingshendelseJsonMigreringTest(
    migrering = V43__rename_behandlingsresultat_henlagt()
) {

    @Test
    fun `renamer behandlingsresultat HENLAGT til IKKE_REALITETSBEHANDLET`() {
        leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.putNull("behandlingsresultat")
            data.put("behandlingstatus", "REGISTRERT")

        }

        val henlagtRad = leggTilBehandlingshendelse(behandlingId = UUID.randomUUID()) { data ->
            data.put("behandlingsresultat", "HENLAGT")
            data.put("behandlingstatus", "AVSLUTTET")
        }

        migrer()

        assertKorrigert(henlagtRad) { gammel, ny ->
            assertEquals("HENLAGT", gammel.path("behandlingsresultat").asText())
            assertEquals("IKKE_REALITETSBEHANDLET", ny.path("behandlingsresultat").asText())
        }
    }
}