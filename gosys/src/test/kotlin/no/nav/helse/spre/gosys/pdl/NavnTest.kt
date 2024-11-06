package no.nav.helse.spre.gosys.pdl

import com.github.navikt.tbd_libs.result_object.ok
import com.github.navikt.tbd_libs.speed.PersonResponse
import com.github.navikt.tbd_libs.speed.SpeedClient
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.spre.gosys.annullering.hentNavn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate

internal class NavnTest {

    @Test
    fun `formatterer navn riktig`() {
        mockk<SpeedClient> {
            every { hentPersoninfo(any(), any()) } returns PersonResponse(
                fødselsdato = LocalDate.now(),
                dødsdato = null,
                fornavn = "FORNAVN",
                mellomnavn = null,
                etternavn = "ETTERNAVN",
                adressebeskyttelse = PersonResponse.Adressebeskyttelse.UGRADERT,
                kjønn = PersonResponse.Kjønn.UKJENT
            ).ok()
        }.also { client ->
            val navn = hentNavn(client, "ident", "callId")
            assertEquals("Fornavn Etternavn", navn)
        }

        mockk<SpeedClient> {
            every { hentPersoninfo(any(), any()) } returns PersonResponse(
                fødselsdato = LocalDate.now(),
                dødsdato = null,
                fornavn = "FORNAVN",
                mellomnavn = "flere mellomnavn",
                etternavn = "ETTERNAVN",
                adressebeskyttelse = PersonResponse.Adressebeskyttelse.UGRADERT,
                kjønn = PersonResponse.Kjønn.UKJENT
            ).ok()
        }.also { client ->
            val navn = hentNavn(client, "ident", "callId")
            assertEquals("Fornavn Flere Mellomnavn Etternavn", navn)
        }
    }
}