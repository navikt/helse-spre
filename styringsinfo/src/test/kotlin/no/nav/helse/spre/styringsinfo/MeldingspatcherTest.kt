package no.nav.helse.spre.styringsinfo

import com.fasterxml.jackson.databind.node.ObjectNode

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class MeldingspatcherTest {

    @Test
    fun `filtrerer vekk fnr i SendtSøknad`() {
        val input = SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T10:00:00.0"),
            korrigerer = UUID.fromString("4c6f931d-63b6-3ff7-b3bc-74d1ad627201"),
            fnr = "12345678910",
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
            melding = """
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
        )

        val result = input.patch(::fjernFnrFraJsonString, "v.1")
        assertEquals(input.sendt, result.sendt)
        assertEquals(input.fom, result.fom)
        assertEquals(input.tom, result.tom)
        assertEquals(input.hendelseId, result.hendelseId)
        assertEquals(input.korrigerer, result.korrigerer)
        assertEquals("v.1", result.patchLevel)
        assertEquals(
            deformaterJson(
                """
            {
              "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """
            ), result.melding
        )
    }


    @Test
    fun `filtrerer vekk fødselsnummer i SendtSøknad`() {
        val input = SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T10:00:00.0"),
            korrigerer = UUID.fromString("4c6f931d-63b6-3ff7-b3bc-74d1ad627201"),
            fnr = "12345678910",
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.fromString("08a92c25-0e59-452f-ba60-83b7515de8e5"),
            melding = """
            {
              "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fødselsnummer": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """
        )

        val result = input.patch(::fjernFnrFraJsonString, "v.1")

        assertEquals(
            deformaterJson(
                """
            {
              "@id": "08a92c25-0e59-452f-ba60-83b7515de8e5",
              "sendtArbeidsgiver": "2023-06-01T10:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """
            ), result.melding
        )
    }
}


private val verdierSomSkalBort = listOf("fnr", "fødselsnummer")

private fun fjernFnrFraJsonString(soknad: SendtSøknad): SendtSøknad {
    val objectNode = objectMapper.readTree(soknad.melding) as ObjectNode
    verdierSomSkalBort.map { objectNode.remove(it) }
    val jsonUtenFnr = objectMapper.writeValueAsString(objectNode)
    return soknad.copy(melding = jsonUtenFnr)
}

private fun deformaterJson(jsonString: String): String {
    return objectMapper.writeValueAsString(objectMapper.readTree(jsonString))
}

private fun SendtSøknad.patch(patchFunction: (input: SendtSøknad) -> SendtSøknad, patchLevel: String): SendtSøknad {
    return patchFunction(this).copy(patchLevel = patchLevel)
}
