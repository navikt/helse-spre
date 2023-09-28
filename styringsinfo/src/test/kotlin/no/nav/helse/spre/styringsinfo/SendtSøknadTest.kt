package no.nav.helse.spre.styringsinfo

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.skyscreamer.jsonassert.JSONAssert
import org.skyscreamer.jsonassert.JSONCompareMode.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class SendtSøknadTest {

    @Test
    fun `hvis SendtSøknad ikke matcher patchLevel skal meldingen være uendret`() {
        val input = enSendtSøknad(enMeldingMedFnr()).copy(patchLevel = 42)
        val result = input.patch(null, ::fjernFnrFraJsonString, 1)
        assertEquals(input, result)
    }

    @Test
    fun `filtrerer vekk fnr i SendtSøknad på upatchede meldinger - og setter patchLevel`() {
        val input = enSendtSøknad(enMeldingMedFnr())

        val result = input.patch(null, ::fjernFnrFraJsonString, 1)

        JSONAssert.assertEquals(enMeldingUtenFnr(), result.melding, STRICT)
        assertEquals(1, result.patchLevel)
    }


    private fun enMeldingMedFnr() = """
            {
              "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
            """

    private fun enSendtSøknad(meldingen: String) =
        SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T10:00:00.0"),
            korrigerer = UUID.fromString("4c6f931d-63b6-3ff7-b3bc-74d1ad627201"),
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
            melding = meldingen,
            patchLevel = null
        )

    private fun enMeldingUtenFnr() = """
                {
                  "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
                  "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
                  "sendtNav": null,
                  "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
                  "fom": "2023-06-05",
                  "tom": "2023-06-11"
                }
            """
}
