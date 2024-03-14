package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import junit.framework.TestCase.assertEquals
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class V35BehandlingsmetodeAvsluttetMedVedtakTest: BehandlingshendelseJsonMigreringTest(
    migrering = V35__riktig_periodetype_for_revurderte_førstegangsbehandlinger(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om revurderinger av førstegangsbehandlinger skal ha periodetype førstebehandling`() {
        val behandlingId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val førstegangsbehandlig = leggTilBehandlingshendelse(
            sakId, behandlingId, true, Versjon.of("0.1.0"), false, data = {
                it.put("periodetype", "FØRSTEGANGSBEHANDLING")
            },
            hendelse = VedtakFattet(
                id = behandlingId,
                opprettet = LocalDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                behandlingId = UUID.randomUUID()
            )
        )
        val behandlingId2 = UUID.randomUUID()
        val revurdering = leggTilBehandlingshendelse(
            sakId, behandlingId2, true, Versjon.of("0.1.0"), false, data = {
                it.put("periodetype", "FORLENGELSE")
            },
            hendelse = VedtakFattet(
                id = behandlingId2,
                opprettet = LocalDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                behandlingId = UUID.randomUUID()
            )
        )

        migrer()
        assertKorrigert(revurdering) { _, ny ->
            assertEquals("FØRSTEGANGSBEHANDLING", ny.path("periodetype").asText())
        }
    }

}