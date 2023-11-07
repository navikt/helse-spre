package no.nav.helse.spre.styringsinfo.domain

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class SendtSøknadTest {
    @Test
    fun `patch sendtSøknad på patchlevel 1 slik at den får nytt patch-level og oppdatert json-melding`() {
        val sendtSøknad = SendtSøknad(
            sendt = LocalDateTime.now(),
            korrigerer = null,
            fom = LocalDate.now(),
            tom = LocalDate.now(),
            hendelseId = UUID.randomUUID(),
            melding = """
                {
                  "@id": "123",
                  "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
                  "sendtNav": null,
                  "korrigerer": null,
                  "fnr": "12345678910",
                  "fom": "2023-06-05",
                  "tom": "2023-06-11",
                  "arbeidsgiver": {
                    "navn": "Nærbutikken AS",
                    "orgnummer": "810007842"
                  }
                }
                """,
            patchLevel = 1
        )
        val patched = sendtSøknad.patch()
        assertEquals(2, patched.patchLevel)

        // Merk at fnr ikke fjernes ettersom dette er en del av patchlevel 1, som ikke blir kjørt her.
        val json = """
            {
              "@id": "123",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": null,
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
            """

        JSONAssert.assertEquals(json, patched.melding, JSONCompareMode.STRICT)
    }

}