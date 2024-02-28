package db.migration

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import junit.framework.TestCase.assertEquals
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.VedtaksperiodeBeslutning
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

internal class V32BehandlingsstatusVedtaksperiodeGodkjentTest: BehandlingshendelseJsonMigreringTest(
    migrering = V32__behandlingsstatus_vedtaksperiode_godkjent(),
    dataSource = dataSource
) {
    @Test
    fun `skal skrive om alle vedtaksperiode_godkjent-hendelser sin behandlingsstatus fra AVSLUTTET til GODKJENT`() {
        val hendelseId = UUID.randomUUID()
        val korrigertHendelse = leggTilBehandlingshendelse(
            UUID.randomUUID(), hendelseId, true, Versjon.of("0.1.0"), false, data = {
                it.put("behandlingstatus", "AVSLUTTET")
            },
            hendelse = VedtaksperiodeBeslutning(
                id = hendelseId,
                opprettet = LocalDateTime.now(),
                data = jacksonObjectMapper().createObjectNode() as JsonNode,
                vedtaksperiodeId = UUID.randomUUID(),
                saksbehandlerEnhet = "nei",
                beslutterEnhet = "nope",
                automatiskBehandling = true,
                behandlingsresultat = Behandling.Behandlingsresultat.VEDTATT,
                eventName = "vedtaksperiode_godkjent",
                behandlingstatus = Behandling.Behandlingstatus.GODKJENT
            )
        )

        migrer()
        assertKorrigert(korrigertHendelse) { _, ny ->
            assertEquals("GODKJENT", ny.path("behandlingstatus").asText())
        }
    }

}