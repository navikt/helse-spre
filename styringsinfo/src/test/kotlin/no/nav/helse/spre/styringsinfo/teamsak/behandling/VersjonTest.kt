package no.nav.helse.spre.styringsinfo.teamsak.behandling

import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.Fjern
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.LeggTil
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.LeggTilOgFjern
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.Major
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.Minor
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.Patch
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.Versjonsutleder
import no.nav.helse.spre.styringsinfo.teamsak.behandling.Versjon.Companion.genererVersjoner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

internal class VersjonTest {

    @Test
    fun `kan ikke lag ugyldige versjoner`() {
        assertEquals("Ugyldig versjon tull", assertThrows<IllegalStateException> { Versjon.of("tull") }.message)
        assertEquals("Ugyldig major a.0.0", assertThrows<IllegalStateException> { Versjon.of("a.0.0") }.message)
        assertEquals("Ugyldig minor 0.a.0", assertThrows<IllegalStateException> { Versjon.of("0.a.0") }.message)
        assertEquals("Ugyldig patch 0.0.a", assertThrows<IllegalStateException> { Versjon.of("0.0.a") }.message)
        assertEquals("Ugyldig patch 0.0.-1", assertThrows<IllegalStateException> { Versjon.of("0.0.-1") }.message)
    }

    @Test
    fun `sammenligne versjoner`() {
        val versjoner = listOf(
            Versjon.of("0.0.1"),
            Versjon.of("0.1.0"),
            Versjon.of("1.0.0"),
            Versjon.of("2.0.0"),
            Versjon.of("1.0.10")
        )
        assertEquals(Versjon.of("2.0.0"), versjoner.max())
        assertEquals(Versjon.of("0.0.1"), versjoner.min())
    }

    @Test
    fun `Evaluerer versjon ut i fra felter`() {
        assertEquals(Versjon.of("0.0.4"), Versjon.of(initielleFelter))
        assertEquals(Versjon.of("0.1.0"), Versjon.of(initielleFelter + "saksbehandlerEnhet" + "beslutterEnhet"))
        assertEquals(Versjon.of("0.2.0"), Versjon.of(initielleFelter + "saksbehandlerEnhet" + "beslutterEnhet" + "periodetype"))
        assertEquals(Versjon.of("0.4.0"), Versjon.of(initielleFelter + "saksbehandlerEnhet" + "beslutterEnhet" + "periodetype" + "mottaker"))
        assertEquals(Versjon.of("0.7.0"), Versjon.of(initielleFelter + "saksbehandlerEnhet" + "beslutterEnhet" + "periodetype" + "mottaker" + "hendelsesmetode"))
        assertEquals(Versjon.of("1.0.0"), Versjon.of(initielleFelter + "saksbehandlerEnhet" + "beslutterEnhet" + "periodetype" + "mottaker" + "hendelsesmetode" + "behandlingstype" - "behandlingtype"))
    }

    @Test
    fun `Feiler om versjon for felter ikke er definert`() {
        assertThrows<IllegalStateException> { Versjon.of(initielleFelter - "aktørId") }
        assertThrows<IllegalStateException> { Versjon.of(initielleFelter + "finnesIkke") }
        assertThrows<IllegalStateException> { Versjon.of(initielleFelter + "finnesIkke" - "aktørId") }
    }

    private val initielleFelter = setOf("aktørId", "mottattTid", "registrertTid", "behandlingstatus", "behandlingtype", "behandlingskilde", "behandlingsmetode", "relatertBehandlingId", "behandlingsresultat")


    @Test
    fun `Utviklingen av versjonering med og uten endringer i felter`() {
        val versjoner = mutableListOf<Versjonsutleder>()
        assertThrows<IllegalStateException> { versjoner.of("a") }
        versjoner.add(Versjonsutleder { _, _ -> setOf("a") to Versjon.of("0.0.5") })
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        versjoner.add(LeggTil("b"))
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        assertEquals(Versjon.of("0.1.0"), versjoner.of("a", "b"))
        versjoner.add(Patch("Bare gjorde en liten ting uten endring i felter"))
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        assertEquals(Versjon.of("0.1.1"), versjoner.of("a", "b"))
        versjoner.add(Patch("Bare gjorde en liten ting uten endring i felter"))
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        assertEquals(Versjon.of("0.1.2"), versjoner.of("a", "b"))
        versjoner.add(Minor("Her gjorde vi en signifikant ting uten endring i felter"))
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        assertEquals(Versjon.of("0.2.0"), versjoner.of("a", "b"))
        versjoner.add(Major("Ja, du vet - Her gjorde vi noe breaking changes uten endring i felter"))
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        assertEquals(Versjon.of("1.0.0"), versjoner.of("a", "b"))
        versjoner.add(Fjern("a"))
        assertEquals(Versjon.of("0.0.5"), versjoner.of("a"))
        assertEquals(Versjon.of("1.0.0"), versjoner.of("a", "b"))
        assertEquals(Versjon.of("2.0.0"), versjoner.of("b"))
        versjoner.add(LeggTilOgFjern(leggTil = setOf("a"), fjern = setOf("b")))
        assertEquals(Versjon.of("3.0.0"), versjoner.of("a"))
    }

    private fun List<Versjonsutleder>.of(vararg felter: String) =
        genererVersjoner[felter.toSet()] ?: throw IllegalStateException("Fant ikke versjon for feltene $felter")
}