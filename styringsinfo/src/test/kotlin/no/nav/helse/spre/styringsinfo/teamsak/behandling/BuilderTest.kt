package no.nav.helse.spre.styringsinfo.teamsak.behandling

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.IKKE_REALITETSBEHANDLET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID
import no.nav.helse.spre.styringsinfo.teamsak.enhet.FunnetTilknytning
import no.nav.helse.spre.styringsinfo.teamsak.enhet.ManglendeTilknytning

internal class BuilderTest {

    @Test
    fun `ignorerer funksjonelt like behandlinger`() {
        val forrige = lagBehandling()
        assertNull(Behandling.Builder(forrige).build(nå.plusDays(1), AUTOMATISK))
        val ny = Behandling.Builder(forrige).tilknytninger(saksbehandler = FunnetTilknytning(enhet = "1234", avdeling = "ab123c")).build(nå.plusDays(1), AUTOMATISK)
        assertNotNull(ny)
        assertEquals("1234", ny!!.saksbehandlerEnhet)
    }

    @Test
    fun `får ikke en feil om man prøver å legge til ny rad etter at noe er avsluttet`() {
        val forrige = lagBehandling().copy(behandlingsresultat = Behandling.Behandlingsresultat.INNVILGET, behandlingstatus = Behandling.Behandlingstatus.AVSLUTTET)
        assertNull(Behandling.Builder(forrige).tilknytninger(saksbehandler = FunnetTilknytning(enhet = "1234", avdeling = "ab123c")).build(etterpå, MANUELL))
    }

    @Test
    fun `behandlingsmetode utledes fra tilknytninger, mens hendelsemetode mates direkte inn`() {
        Behandling.Builder(lagBehandling()).tilknytninger().build(TOTRINNS).let {
            assertNull(it.saksbehandlerEnhet)
            assertNull(it.saksbehandlerAvdeling)
            assertNull(it.beslutterEnhet)
            assertNull(it.beslutterAvdeling)
            assertEquals(AUTOMATISK, it.behandlingsmetode)
            assertEquals(TOTRINNS, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).tilknytninger(saksbehandler = ManglendeTilknytning).build(TOTRINNS).let {
            assertNull(it.saksbehandlerEnhet)
            assertNull(it.saksbehandlerAvdeling)
            assertNull(it.beslutterEnhet)
            assertNull(it.beslutterAvdeling)
            assertEquals(MANUELL, it.behandlingsmetode)
            assertEquals(TOTRINNS, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).tilknytninger(saksbehandler = FunnetTilknytning(enhet = "1234", avdeling = "ab123c")).build(TOTRINNS).let {
            assertEquals("1234", it.saksbehandlerEnhet)
            assertEquals("ab123c", it.saksbehandlerAvdeling)
            assertNull(it.beslutterEnhet)
            assertNull(it.beslutterAvdeling)
            assertEquals(MANUELL, it.behandlingsmetode)
            assertEquals(TOTRINNS, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).tilknytninger(saksbehandler = ManglendeTilknytning, beslutter = ManglendeTilknytning).build(AUTOMATISK).let {
            assertNull(it.saksbehandlerEnhet)
            assertNull(it.saksbehandlerAvdeling)
            assertNull(it.beslutterEnhet)
            assertNull(it.beslutterAvdeling)
            assertEquals(TOTRINNS, it.behandlingsmetode)
            assertEquals(AUTOMATISK, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).tilknytninger(
            saksbehandler = FunnetTilknytning(enhet = "1234", avdeling = "ab123c"),
            beslutter = FunnetTilknytning(enhet = "5678", avdeling = "ab123d")).build(AUTOMATISK).let {
            assertEquals("1234", it.saksbehandlerEnhet)
            assertEquals("ab123c", it.saksbehandlerAvdeling)
            assertEquals("5678", it.beslutterEnhet)
            assertEquals("ab123d", it.beslutterAvdeling)
            assertEquals(TOTRINNS, it.behandlingsmetode)
            assertEquals(AUTOMATISK, it.hendelsesmetode)
        }
    }

    private val nå = OffsetDateTime.now()
    private val etterpå = nå.plusDays(1)

    // Setter bare resultat for å vite at det ikke blir null 💡
    private fun Behandling.Builder.build(hendelsemetode: Behandling.Metode) =
        behandlingsresultat(IKKE_REALITETSBEHANDLET).build(OffsetDateTime.now(), hendelsemetode)!!

    private fun lagBehandling(yrkesaktivitetstype: String = "ARBEIDSTAKER") = Behandling(
        sakId = SakId(UUID.randomUUID()),
        behandlingId = BehandlingId(UUID.randomUUID()),
        relatertBehandlingId = null,
        aktørId = "1",
        mottattTid = nå,
        registrertTid = nå,
        funksjonellTid = nå,
        behandlingstatus = Behandling.Behandlingstatus.REGISTRERT,
        behandlingsmetode = AUTOMATISK,
        behandlingskilde = Behandling.Behandlingskilde.SYKMELDT,
        periodetype = Behandling.Periodetype.FORLENGELSE,
        behandlingstype = Behandling.Behandlingstype.SØKNAD,
        behandlingsresultat = null,
        mottaker = null,
        saksbehandlerEnhet = null,
        beslutterEnhet = null,
        hendelsesmetode = AUTOMATISK,
        yrkesaktivitetstype = yrkesaktivitetstype,
    )
}
