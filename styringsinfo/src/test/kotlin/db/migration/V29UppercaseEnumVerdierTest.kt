package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class V29UppercaseEnumVerdierTest: BehandlingshendelseJsonMigreringTest(
    migrering = V29__uppercase_enum_verdier()
) {

    @Test
    fun `Legger til korrigerende rader for å rette opp i feil enum-verdier i versjon 0_0_1`() {
        val behandlingId1 = UUID.randomUUID()
        val behandling1Rad1 = leggTilRad(behandlingId1, false, behandlingsresultat = null)
        val behandling1Rad2 = leggTilRad(behandlingId1, true)

        val behandlingId2 = UUID.randomUUID()
        val behandling2Rad1 = leggTilRad(behandlingId2, false)
        val behandling2Rad2 = leggTilRad(behandlingId2, false)
        val behandling2Rad3 = leggTilRad(behandlingId2, true)

        migrer()

        assertKorrigert(behandling1Rad1) { gammel, ny -> assertGammelOgNyData(gammel, ny) }
        assertKorrigert(behandling1Rad2) { gammel, ny -> assertGammelOgNyData(gammel, ny) }
        assertKorrigert(behandling2Rad1) { gammel, ny -> assertGammelOgNyData(gammel, ny) }
        assertKorrigert(behandling2Rad2) { gammel, ny -> assertGammelOgNyData(gammel, ny) }
        assertKorrigert(behandling2Rad3) { gammel, ny -> assertGammelOgNyData(gammel, ny) }
    }

    @Test
    fun `Migrerer kun behandlingshendelser med versjon 0_0_1`() {
        val behandlingId = UUID.randomUUID()
        leggTilRad(behandlingId, false, "0.0.2")
        migrer()
    }

    @Test
    fun `Skal ikke legge til korrigerende rader for rader som allerede er korrigert`() {
        val behandlingId = UUID.randomUUID()
        val funksjonellTid = LocalDateTime.now()
        leggTilRad(behandlingId, false, "0.0.1", erKorrigert = true, funksjonellTid = funksjonellTid)
        val raden = leggTilRad(behandlingId, true, "0.0.1", erKorrigert = false, funksjonellTid = funksjonellTid)
        migrer()
        assertKorrigert(raden) { gammel, ny -> assertGammelOgNyData(gammel, ny) }
    }

    private fun assertGammelOgNyData(gammel: ObjectNode, ny: ObjectNode) {
        assertEquals("Automatisk", gammel.path("behandlingsmetode").asText())
        assertEquals("AUTOMATISK", ny.path("behandlingsmetode").asText())
        assertEquals("AvventerGodkjenning", gammel.path("behandlingstatus").asText())
        assertEquals("AVVENTER_GODKJENNING", ny.path("behandlingstatus").asText())
        assertEquals("Saksbehandler", gammel.path("behandlingskilde").asText())
        assertEquals("SAKSBEHANDLER", ny.path("behandlingskilde").asText())
        assertEquals("Omgjøring", gammel.path("behandlingtype").asText())
        assertEquals("OMGJØRING", ny.path("behandlingtype").asText())
        if (gammel.path("behandlingsresultat").isNull) {
            assertTrue(ny.path("behandlingsresultat").isNull)
        } else {
            assertEquals("Vedtatt", gammel.path("behandlingsresultat").asText())
            assertEquals("VEDTATT", ny.path("behandlingsresultat").asText())
        }
    }

    private fun leggTilRad(behandlingId: UUID, siste: Boolean, versjon: String = "0.0.1", erKorrigert: Boolean = false, funksjonellTid: LocalDateTime = LocalDateTime.now(), behandlingsresultat: String? = "Vedtatt") =
        leggTilBehandlingshendelse(behandlingId = behandlingId, siste = siste, versjon = Versjon.Companion.of(versjon), erKorrigert = erKorrigert, funksjonellTid = funksjonellTid) {
            val epoch = LocalDate.EPOCH.atStartOfDay()
            it.put("aktørId", "1234")
            it.put("mottattTid", "$epoch")
            it.put("registrertTid", "$epoch")
            it.put("behandlingstatus", "AvventerGodkjenning")
            it.put("behandlingtype", "Omgjøring")
            it.put("behandlingskilde", "Saksbehandler")
            it.putString("behandlingsmetode", "Automatisk")
            it.putString("relatertBehandlingId", null)
            it.putString("behandlingsresultat", behandlingsresultat)
        }

    private fun ObjectNode.putString(fieldName: String, value: String?): ObjectNode {
        return if (value == null) putNull(fieldName)
        else put(fieldName, value)
    }
}