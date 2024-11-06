package db.migration

import no.nav.helse.spre.styringsinfo.teamsak.Hendelsefabrikk
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.*

internal class V30KorrigererBehandlingsresultatVedVedtaksperiodeAvvistTest: BehandlingshendelseJsonMigreringTest(
    migrering = V30__korrigerer_behandlingsresultat_ved_vedtaksperiode_avvist()
) {

    @Test
    fun `endrer behandlingsresultat til AVBRUTT for hendelser som feilaktig har behandlingsresultat VEDTATT`() {
        val behandlingId1 = UUID.randomUUID()
        val behandlingId2 = UUID.randomUUID()
        val vedtaksperiodeAvvist = Hendelsefabrikk().vedtaksperiodeAvvist()

        val rad1behandling1 = leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = true, versjon = Versjon.Companion.of("1.2.3"), hendelse = vedtaksperiodeAvvist) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt", true)
        }

        // Skal ikke migreres siden erKorrigert = true
        leggTilBehandlingshendelse(behandlingId = behandlingId2, siste = false, erKorrigert = true, versjon = Versjon.Companion.of("9.2.3"), hendelse = vedtaksperiodeAvvist) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt2", true)
        }
        val rad2behandling2 = leggTilBehandlingshendelse(behandlingId = behandlingId2, siste = false, versjon = Versjon.Companion.of("9.2.3"), hendelse = vedtaksperiodeAvvist) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt2", true)
        }

        // Skal ikke migreres siden den ei peker på vedtaksperiode_avvist-hendelse
        leggTilBehandlingshendelse(behandlingId = behandlingId1, siste = true, versjon = Versjon.Companion.of("1.2.3")) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt", true)
        }

        migrer()
        assertKorrigert(rad1behandling1) { gammel, ny ->
            assertEquals("VEDTATT", gammel.path("behandlingsresultat").asText())
            assertTrue(gammel.path("uendretFelt").asBoolean())
            assertEquals("AVBRUTT", ny.path("behandlingsresultat").asText())
            assertTrue(ny.path("uendretFelt").asBoolean())
        }
        assertKorrigert(rad2behandling2) { gammel, ny ->
            assertEquals("VEDTATT", gammel.path("behandlingsresultat").asText())
            assertTrue(gammel.path("uendretFelt2").asBoolean())
            assertEquals("AVBRUTT", ny.path("behandlingsresultat").asText())
            assertTrue(ny.path("uendretFelt2").asBoolean())
        }
    }
}