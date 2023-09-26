package no.nav.helse.spre.styringsinfo

import org.apache.kafka.common.network.Send
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
        """.trimIndent()
        )

        val result = input.patch(::noOp, "v.1")
        assertEquals(input.sendt, result.sendt)
        assertEquals(input.fom, result.fom)
        assertEquals(input.tom, result.tom)
        assertEquals(input.hendelseId, result.hendelseId)
        assertEquals(input.korrigerer, result.korrigerer)
        assertEquals(input.melding, result.melding)
        assertEquals("v.1", result.patchLevel)
    }

    fun noOp(sendtSøknad: SendtSøknad): SendtSøknad = sendtSøknad
}


private fun SendtSøknad.patch(patchFunction: (input: SendtSøknad) -> SendtSøknad, patchLevel: String): SendtSøknad {
    return patchFunction(this).copy(patchLevel = patchLevel)
}
