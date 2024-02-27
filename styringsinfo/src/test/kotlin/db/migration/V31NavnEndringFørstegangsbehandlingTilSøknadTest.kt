package db.migration

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.spre.styringsinfo.db.AbstractDatabaseTest.Companion.dataSource
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon
import no.nav.helse.spre.styringsinfo.teamsak.hendelse.GenerasjonOpprettet
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class V31NavnEndringFørstegangsbehandlingTilSøknadTest: BehandlingshendelseJsonMigreringTest(
    migrering = V31__navnendring_førstegangsbehandling_til_søknad(),
    dataSource = dataSource
) {

    @Test
    fun `endrer navn fra FØRSTEGANGSBEHANDLING til SØKNAD`() {
        val behandlingId1 = UUID.randomUUID()
        val generasjonOpprettet = GenerasjonOpprettet(
            id = UUID.randomUUID(),
            opprettet = LocalDateTime.now(),
            data = jacksonObjectMapper().createObjectNode(),
            vedtaksperiodeId = UUID.randomUUID(),
            generasjonId = UUID.randomUUID(),
            aktørId = "aktør",
            generasjonkilde = GenerasjonOpprettet.Generasjonkilde(LocalDateTime.now(), LocalDateTime.now(), GenerasjonOpprettet.Avsender("SYKMELDT")),
            generasjonstype = GenerasjonOpprettet.Generasjonstype("FØRSTEGANGSBEHANDLING")
        )

        val rad1behandling1 = leggTilRad(behandlingId = behandlingId1, siste = true, versjon = Versjon.Companion.of("1.2.3"), hendelse = generasjonOpprettet) {
            it.put("behandlingtype", "FØRSTEGANGSBEHANDLING")
            it.put("uendretFelt", true)
        }

        migrer()
        assertKorrigert(rad1behandling1) { gammel, ny ->
            assertEquals("FØRSTEGANGSBEHANDLING", gammel.path("behandlingtype").asText())
            assertTrue(gammel.path("uendretFelt").asBoolean())
            assertEquals("SØKNAD", ny.path("behandlingtype").asText())
            assertTrue(ny.path("uendretFelt").asBoolean())
        }
    }
}