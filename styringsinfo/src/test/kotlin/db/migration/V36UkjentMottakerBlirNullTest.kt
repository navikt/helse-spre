package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.isMissingOrNull
import no.nav.helse.spre.styringsinfo.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtakFattet
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class V36UkjentMottakerBlirNullTest: BehandlingshendelseJsonMigreringTest(
    migrering = V36__ukjent_mottaker_blir_null(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om revurderinger av førstegangsbehandlinger skal ha periodetype førstebehandling`() {
        val behandlingId = UUID.randomUUID()
        val sakId = UUID.randomUUID()
        val behandling = leggTilBehandlingshendelse(
            sakId, behandlingId, true, Versjon.of("0.1.0"), false, data = {
                it.put("mottaker", "UKJENT")
            },
            hendelse = VedtakFattet(
                id = behandlingId,
                opprettet = LocalDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                behandlingId = UUID.randomUUID(),
                tags = emptyList()
            )
        )

        migrer()
        assertKorrigert(behandling) { _, ny ->
            assertTrue(ny.path("mottaker").isMissingOrNull())
        }
    }

}