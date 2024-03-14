package no.nav.helse.spre.styringsinfo.teamsak

import no.nav.helse.spre.styringsinfo.teamsak.NavOrganisjonsmasterClient.Companion.enhet
import no.nav.helse.spre.styringsinfo.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate

class NavOrganisjonsmasterClientTest {

    @Test
    fun `parser enhet i et enkelt case`() {
        val dato = LocalDate.of(2024, 1, 1)
        val actual = objectMapper.readTree(personMedEnEnhet).enhet(dato)
        assertEquals("6650", actual)
    }

    @Test
    fun `person uten enhet`() {
        val dato = LocalDate.of(2024, 1, 1)
        val actual = objectMapper.readTree(personUtenEnhet).enhet(dato)
        assertEquals(null, actual)
    }
    @Test
    fun `person uten enhet på dato`() {
        val dato = LocalDate.of(2019, 1, 1)
        val actual = objectMapper.readTree(personMedEnEnhet).enhet(dato)
        assertEquals(null, actual)
    }

    @Test
    fun `parser enhet med flere enhetstyper`() {
        val dato = LocalDate.of(2024, 1, 1)
        val actual = objectMapper.readTree(personMedFlereEnheter).enhet(dato)
        assertEquals("4200", actual)
    }

    @Test
    fun `parser enhet når det er første dag i jobben`() {
        val dato = LocalDate.of(2020, 10, 26)
        val actual = objectMapper.readTree(personMedEnEnhet).enhet(dato)
        assertEquals("1350", actual)
    }

    @Test
    fun `parser enhet når det er siste dag i jobben`() {
        val dato = LocalDate.of(2022, 11, 27)
        val actual = objectMapper.readTree(personMedEnEnhet).enhet(dato)
        assertEquals("1350", actual)
    }


    @Language("JSON")
    private val personMedEnEnhet = """
        {
          "data": {
            "ressurs": {
              "navident": "X999999",
              "orgTilknytning": [
                {
                  "gyldigFom": "2020-10-26",
                  "gyldigTom": "2022-11-27",
                  "orgEnhet": {
                    "id": "ab666a",
                    "navn": "OI utvikling 1",
                    "remedyEnhetId": "1350",
                    "orgEnhetsType": "DIREKTORAT"
                  }
                },
                {
                  "gyldigFom": "2022-11-28",
                  "gyldigTom": null,
                  "orgEnhet": {
                    "id": "ab666a",
                    "navn": "OI utvikling 2",
                    "remedyEnhetId": "6650",
                    "orgEnhetsType": "DIREKTORAT"
                  }
                }
              ]
            }
          }
        }
        
    """.trimIndent()

    @Language("JSON")
    private val personMedFlereEnheter = """
        {
          "data": {
            "ressurs": {
              "navident": "X999999",
              "orgTilknytning": [
                {
                  "gyldigFom": "2022-10-28",
                  "gyldigTom": null,
                  "orgEnhet": {
                    "id": "ab666a",
                    "navn": "OI utvikling 1",
                    "remedyEnhetId": "6650",
                    "orgEnhetsType": "DIREKTORAT"
                  }
                },
                {
                  "gyldigFom": "2022-11-28",
                  "gyldigTom": null,
                  "orgEnhet": {
                    "id": "ab666a",
                    "navn": "OI utvikling 2",
                    "remedyEnhetId": "4200",
                    "orgEnhetsType": "NAV_ARBEID_OG_YTELSER"
                  }
                }
              ]
            }
          }
        }
        
    """.trimIndent()

    @Language("JSON")
    private val personUtenEnhet = """
        {
          "data": {
            "ressurs": {
              "navident": "X999999",
              "orgTilknytning": []
            }
          }
        }
    """.trimIndent()
}

