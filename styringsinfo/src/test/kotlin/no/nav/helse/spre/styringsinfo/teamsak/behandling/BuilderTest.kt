package no.nav.helse.spre.styringsinfo.teamsak.behandling

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class BuilderTest {

    @Test
    fun `ignorerer funksjonelt like behandlinger`() {
        val nå = LocalDateTime.now()
        val forrige = Behandling(
            sakId = SakId(UUID.randomUUID()),
            behandlingId = BehandlingId(UUID.randomUUID()),
            relatertBehandlingId = null,
            aktørId = "1",
            mottattTid = nå,
            registrertTid = nå,
            funksjonellTid = nå,
            behandlingstatus = Behandling.Behandlingstatus.REGISTRERT,
            behandlingsmetode = Behandling.Behandlingsmetode.AUTOMATISK,
            behandlingskilde = Behandling.Behandlingskilde.SYKMELDT,
            periodetype = Behandling.Periodetype.FORLENGELSE,
            behandlingstype = Behandling.Behandlingstype.SØKNAD,
            behandlingsresultat = null,
            mottaker = null,
            saksbehandlerEnhet = null,
            beslutterEnhet = null
        )

        assertNull(Behandling.Builder(forrige).build(nå.plusDays(1), Behandling.Behandlingsmetode.MANUELL))
        val ny = Behandling.Builder(forrige).saksbehandlerEnhet("1234").build(nå.plusDays(1), Behandling.Behandlingsmetode.MANUELL)
        assertNotNull(ny)
        assertEquals("1234", ny!!.saksbehandlerEnhet)
    }
}