package no.nav.helse.spre.styringsinfo.teamsak.behandling

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Behandlingsresultat.IKKE_REALITETSBEHANDLET
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Behandling.Metode.*
import no.nav.helse.spre.styringsinfo.teamsak.enhet.FunnetEnhet
import no.nav.helse.spre.styringsinfo.teamsak.enhet.ManglendeEnhet
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime
import java.util.UUID

internal class BuilderTest {

    @Test
    fun `ignorerer funksjonelt like behandlinger`() {
        val forrige = lagBehandling()
        assertNull(Behandling.Builder(forrige).build(nå.plusDays(1), AUTOMATISK, forrige.yrkesaktivitetstype))
        val ny = Behandling.Builder(forrige).enheter(saksbehandler = FunnetEnhet("1234")).build(nå.plusDays(1), AUTOMATISK, "ARBEIDSTAKER")
        assertNotNull(ny)
        assertEquals("1234", ny!!.saksbehandlerEnhet)
    }

    @Test
    fun `får ikke en feil om man prøver å legge til ny rad etter at noe er avsluttet`() {
        val forrige = lagBehandling().copy(behandlingsresultat = Behandling.Behandlingsresultat.INNVILGET, behandlingstatus = Behandling.Behandlingstatus.AVSLUTTET)
        assertNull(Behandling.Builder(forrige).enheter(saksbehandler = FunnetEnhet("1234")).build(etterpå, MANUELL, "ARBEIDSTAKER"))
    }

    @Test
    fun `behandlingsmetode utledes fra enheter, mens hendelsemetode mates direkte inn`() {
        Behandling.Builder(lagBehandling()).enheter().build(TOTRINNS).let {
            assertNull(it.saksbehandlerEnhet)
            assertNull(it.beslutterEnhet)
            assertEquals(AUTOMATISK, it.behandlingsmetode)
            assertEquals(TOTRINNS, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).enheter(saksbehandler = ManglendeEnhet).build(TOTRINNS).let {
            assertNull(it.saksbehandlerEnhet)
            assertNull(it.beslutterEnhet)
            assertEquals(MANUELL, it.behandlingsmetode)
            assertEquals(TOTRINNS, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).enheter(saksbehandler = FunnetEnhet("1234")).build(TOTRINNS).let {
            assertEquals("1234", it.saksbehandlerEnhet)
            assertNull(it.beslutterEnhet)
            assertEquals(MANUELL, it.behandlingsmetode)
            assertEquals(TOTRINNS, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).enheter(saksbehandler = ManglendeEnhet, beslutter = ManglendeEnhet).build(AUTOMATISK).let {
            assertNull(it.saksbehandlerEnhet)
            assertNull(it.beslutterEnhet)
            assertEquals(TOTRINNS, it.behandlingsmetode)
            assertEquals(AUTOMATISK, it.hendelsesmetode)
        }
        Behandling.Builder(lagBehandling()).enheter(saksbehandler = FunnetEnhet("1234"), beslutter = FunnetEnhet("5678")).build(AUTOMATISK).let {
            assertEquals("1234", it.saksbehandlerEnhet)
            assertEquals("5678", it.beslutterEnhet)
            assertEquals(TOTRINNS, it.behandlingsmetode)
            assertEquals(AUTOMATISK, it.hendelsesmetode)
        }
    }

    private val nå = OffsetDateTime.now()
    private val etterpå = nå.plusDays(1)

    // Setter bare resultat for å vite at det ikke blir null 💡
    private fun Behandling.Builder.build(hendelsemetode: Behandling.Metode) =
        behandlingsresultat(IKKE_REALITETSBEHANDLET).build(OffsetDateTime.now(), hendelsemetode, "ARBEIDSTAKER")!!
    private fun lagBehandling() = Behandling(
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
        yrkesaktivitetstype = "ARBEIDSTAKER",
    )
}
