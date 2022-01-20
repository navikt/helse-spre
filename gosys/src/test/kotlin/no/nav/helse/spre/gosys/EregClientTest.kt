package no.nav.helse.spre.gosys

import kotlinx.coroutines.runBlocking
import no.nav.helse.spre.gosys.e2e.AbstractE2ETest
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.*

internal class EregClientTest : AbstractE2ETest() {

    @Test
    fun `happy case`() = runBlocking {
        assertEquals("PENGELØS SPAREBANK", eregClient.hentOrganisasjonsnavn("123456789", UUID.randomUUID()).navn)
    }
}

@Language("Json")
internal fun eregResponse() = """
    {
      "organisasjonsnummer": "123456789",
      "type": "Virksomhet",
      "navn": {
        "redigertnavn": "PSBNK",
        "navnelinje1": "PENGELØS SPAREBANK",
        "bruksperiode": {
          "fom": "2021-07-14T12:39:27.315"
        },
        "gyldighetsperiode": {
          "fom": "2021-07-14"
        }
      },
      "organisasjonDetaljer": {
        "registreringsdato": "2021-07-14T00:00:00",
        "enhetstyper": [
          {
            "enhetstype": "BEDR",
            "bruksperiode": {
              "fom": "2021-07-14T12:29:00.044"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          }
        ],
        "navn": [
          {
            "redigertnavn": "PSBNK",
            "navnelinje1": "PENGELØS SPAREBANK",
            "bruksperiode": {
              "fom": "2021-07-14T12:39:27.315"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          }
        ],
        "forretningsadresser": [
          {
            "type": "Forretningsadresse",
            "adresselinje1": "GRAVDALSLIEN 89",
            "postnummer": "5165",
            "poststed": "LAKSEVÅG",
            "landkode": "NO",
            "kommunenummer": "4601",
            "bruksperiode": {
              "fom": "2021-07-14T12:39:27.311"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          }
        ],
        "postadresser": [
          {
            "type": "Postadresse",
            "adresselinje1": "SPANGRUDVEGEN 38",
            "postnummer": "2930",
            "poststed": "BAGN",
            "landkode": "NO",
            "kommunenummer": "3449",
            "bruksperiode": {
              "fom": "2021-07-14T12:39:27.313"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          }
        ],
        "internettadresser": [
          {
            "adresse": "PENGELOSBANK.NO",
            "bruksperiode": {
              "fom": "2021-07-14T12:39:27.316"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          }
        ],
        "epostadresser": [
          {
            "adresse": "MONEY.PLZ@HUSTLER.IO",
            "bruksperiode": {
              "fom": "2021-07-14T12:39:27.322"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          }
        ],
        "sistEndret": "2021-07-14"
      },
      "virksomhetDetaljer": {
        "enhetstype": "BEDR"
      },
      "inngaarIJuridiskEnheter": [
        {
          "organisasjonsnummer": "928497704",
          "navn": {
            "navnelinje1": "BESK KAFFE",
            "bruksperiode": {
              "fom": "2021-07-14T12:39:27.209"
            },
            "gyldighetsperiode": {
              "fom": "2021-07-14"
            }
          },
          "bruksperiode": {
            "fom": "2021-07-14T12:39:27.314"
          },
          "gyldighetsperiode": {
            "fom": "2021-07-14"
          }
        }
      ]
    }
""".trimIndent()
