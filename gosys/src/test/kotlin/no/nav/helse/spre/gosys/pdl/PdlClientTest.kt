package no.nav.helse.spre.gosys.pdl

import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.util.*

@KtorExperimentalAPI
internal class PdlClientTest: AbstractE2ETest() {

    @Test
    fun `happy case oppslag`() = runBlocking {
        val response = pdlClient.hentPersonNavn("12345678910", UUID.randomUUID())
        assertEquals("Molefonken Ert", response)
    }

    @Test
    fun `formatterer navn riktig`() {
        assertEquals("Fornavn Etternavn" ,PdlNavn("Fornavn", null, "Etternavn").tilVisning())
        assertEquals("Fornavn Etmellomnavn Etternavn" ,PdlNavn("Fornavn", "EtMellomnavn", "EtteRNavn").tilVisning())
        assertEquals("Fornavn Flere Mellomnavn Etternavn" ,PdlNavn("Fornavn", "FlErE MellomNavn", "Etternavn").tilVisning())
    }
}


@Language("JSON")
internal fun pdlResponse() = """
    {
      "data": {
        "hentPerson": {
          "navn": [
            {
              "fornavn": "MOLEFONKEN",
              "mellomnavn": null,
              "etternavn": "ERT"
            }
          ]
        }
      }
    }
""".trimIndent()