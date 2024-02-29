package db.migration

import com.fasterxml.jackson.databind.node.ObjectNode
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class V36BehandlingstypeMedSTest: BehandlingshendelseJsonMigreringTest(
    migrering = V36__behandlingstype_med_s(),
    dataSource = dataSource
) {
    @Test
    fun `endrer navn fra behandlingtype til behandlingstype`() {
        val rad1 = leggTilBehandlingshendelse { it.put("behandlingtype", "STOR_1").put("annetFelt", "liten_1") }
        val rad2 = leggTilBehandlingshendelse { it.put("behandlingtype", "STOR_2").put("annetFelt", "liten_2") }
        val rad3 = leggTilBehandlingshendelse { it.put("behandlingtype", "STOR_3").put("annetFelt", "liten_3") }
        val rad4 = leggTilBehandlingshendelse { it.putNull("behandlingtype").put("annetFelt", "liten_4")}
        leggTilBehandlingshendelse { it.put("behandlingstype", "STOR_4").put("annetFelt", "liten_4") } // allerede behandlingstype
        leggTilBehandlingshendelse() // Har ikke feltet
        val rad7 = leggTilBehandlingshendelse { it.put("behandlingtype", "STOR_7").put("annetFelt", "liten_7") }

        migrer()

        assertKorrigert(rad1) { gammel, ny -> assertGammelOgNy(gammel, ny, 1) }
        assertKorrigert(rad2) { gammel, ny -> assertGammelOgNy(gammel, ny, 2) }
        assertKorrigert(rad3) { gammel, ny -> assertGammelOgNy(gammel, ny, 3) }
        assertKorrigert(rad4) { gammel, ny -> assertGammelOgNy(gammel, ny, 4, forventetBehandlingstype = null) }
        assertKorrigert(rad7) { gammel, ny -> assertGammelOgNy(gammel, ny, 7) }
    }

    private fun assertGammelOgNy(gammel: ObjectNode, ny: ObjectNode, nummer: Int, forventetBehandlingstype: String? = "STOR_$nummer") {
        assertEquals(setOf("behandlingtype", "annetFelt"), gammel.felter)
        assertEquals(setOf("behandlingstype", "annetFelt"), ny.felter)

        assertEquals(forventetBehandlingstype, gammel.path("behandlingtype").takeUnless { it.isNull }?.asText())
        assertEquals(forventetBehandlingstype, ny.path("behandlingstype").takeUnless { it.isNull }?.asText())

        assertEquals("liten_$nummer", gammel.path("annetFelt").asText())
        assertEquals("liten_$nummer", ny.path("annetFelt").asText())
    }

    private val ObjectNode.felter get() = fieldNames().asSequence().toSet()
}