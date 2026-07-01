package no.nav.helse.spre.styringsinfo.teamsak.enhet

import no.nav.helse.spre.styringsinfo.objectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDate
import no.nav.helse.spre.styringsinfo.teamsak.enhet.NavOrganisasjonsmasterClient.Companion.tilknytning

class NavOrganisasjonsmasterClientTest {

    @Test
    fun `parser enhet i et enkelt case`() {
        val dato = LocalDate.of(2024, 1, 1)
        val actualTilknytning = objectMapper.readTree(personMedEnEnhet).tilknytning(dato)
        assertEquals(FunnetTilknytning(enhet = "6650", avdeling = "ab666b"), actualTilknytning)
    }

    @Test
    fun `person uten enhet`() {
        val dato = LocalDate.of(2024, 1, 1)
        val actualTilknytning = objectMapper.readTree(personUtenEnhet).tilknytning(dato)
        assertEquals(ManglendeTilknytning, actualTilknytning)
    }

    @Test
    fun `person uten enhet på dato`() {
        val dato = LocalDate.of(2019, 1, 1)
        val actualTilknytning = objectMapper.readTree(personMedEnEnhet).tilknytning(dato)
        assertEquals(ManglendeTilknytning, actualTilknytning)
    }

    @Test
    fun `parser enhet med flere enhetstyper`() {
        val dato = LocalDate.of(2024, 1, 1)
        val actualTilknytning = objectMapper.readTree(personMedFlereEnheter).tilknytning(dato)
        assertEquals(FunnetTilknytning(enhet = "4200", avdeling = "ab666b"), actualTilknytning)
    }

    @Test
    fun `parser enhet når det er første dag i jobben`() {
        val dato = LocalDate.of(2020, 10, 26)
        val actualTilknytning = objectMapper.readTree(personMedEnEnhet).tilknytning(dato)
        assertEquals(FunnetTilknytning(enhet = "1350", avdeling = "ab666a"), actualTilknytning)
    }

    @Test
    fun `parser enhet når det er siste dag i jobben`() {
        val dato = LocalDate.of(2022, 11, 27)
        val actualTilknytning = objectMapper.readTree(personMedEnEnhet).tilknytning(dato)
        assertEquals(FunnetTilknytning(enhet = "1350", avdeling = "ab666a"), actualTilknytning)
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
                    "id": "ab666b",
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
                    "id": "ab666b",
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

