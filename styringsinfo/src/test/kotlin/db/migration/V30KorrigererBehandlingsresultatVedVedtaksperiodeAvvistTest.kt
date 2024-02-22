package db.migration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.VEDTATT
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeBeslutning
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class V30KorrigererBehandlingsresultatVedVedtaksperiodeAvvistTest: BehandlingshendelseJsonMigreringTest(
    migrering = V30__korrigerer_behandlingsresultat_ved_vedtaksperiode_avvist(),
    dataSource = dataSource
) {

    @Test
    fun `endrer behandlingsresultat til AVBRUTT for hendelser som feilaktig har behandlingsresultat VEDTATT`() {
        val behandlingId1 = UUID.randomUUID()
        val behandlingId2 = UUID.randomUUID()
        val vedtaksperiodeAvvist = VedtaksperiodeBeslutning(UUID.randomUUID(), LocalDateTime.now(), jacksonObjectMapper().createObjectNode(), UUID.randomUUID(), null, null, true, VEDTATT, "vedtaksperiode_avvist")

        val rad1behandling1 = leggTilRad(behandlingId = behandlingId1, siste = true, versjon = Versjon.Companion.of("1.2.3"), hendelse = vedtaksperiodeAvvist) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt", true)
        }

        // Skal ikke migreres siden erKorrigert = true
        leggTilRad(behandlingId = behandlingId2, siste = false, erKorrigert = true, versjon = Versjon.Companion.of("9.2.3"), hendelse = vedtaksperiodeAvvist) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt2", true)
        }
        val rad2behandling2 = leggTilRad(behandlingId = behandlingId2, siste = false, versjon = Versjon.Companion.of("9.2.3"), hendelse = vedtaksperiodeAvvist) {
            it.put("behandlingsresultat", "VEDTATT")
            it.put("uendretFelt2", true)
        }

        // Skal ikke migreres siden den ei peker på vedtaksperiode_avvist-hendelse
        leggTilRad(behandlingId = behandlingId1, siste = true, versjon = Versjon.Companion.of("1.2.3")) {
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