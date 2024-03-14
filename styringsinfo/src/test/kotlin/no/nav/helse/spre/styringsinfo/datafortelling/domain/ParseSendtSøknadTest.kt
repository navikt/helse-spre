package no.nav.helse.spre.styringsinfo.datafortelling.domain

import no.nav.helse.rapids_rivers.JsonMessage
import no.nav.helse.rapids_rivers.MessageProblems
import no.nav.helse.spre.styringsinfo.datafortelling.toSendtSøknadArbeidsgiver
import no.nav.helse.spre.styringsinfo.datafortelling.toSendtSøknadNav
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

class ParseSendtSøknadTest {

    @Test
    internal fun `parser alle felter i sendt_søknad_arbeidsgiver-melding`() {
        val json = """
            {
              "@id": "267bd234-8ec6-4ea2-9f9b-ad0bd4bead68",
              "sendtArbeidsgiver": "2023-06-01T00:00:00.0",
              "sendtNav": null,
              "korrigerer": "4c6f931d-63b6-3ff7-b3bc-74d1ad627201",
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """.trimIndent()
        val message = JsonMessage(json, MessageProblems(json)).apply {
            this.interestedIn("sendtArbeidsgiver", "sendtNav", "korrigerer", "fom", "tom", "@id")
        }
        message.toJson()
        val sendtSøknad = message.toSendtSøknadArbeidsgiver()
        val expected = SendtSøknad(
            sendt = LocalDateTime.parse("2023-06-01T00:00:00.0"),
            korrigerer = UUID.fromString("4c6f931d-63b6-3ff7-b3bc-74d1ad627201"),
            fom = LocalDate.parse("2023-06-05"),
            tom = LocalDate.parse("2023-06-11"),
            hendelseId = UUID.fromString("267bd234-8ec6-4ea2-9f9b-ad0bd4bead68"),
            melding = message.toJson()
        )
        assertEquals(expected, sendtSøknad)
    }

    @Test
    internal fun `parser alle felter i sendt_søknad_nav-melding`() {
        val json = """
            {
              "sendtArbeidsgiver": null,
              "sendtNav": "2023-06-01T00:00:00.0",
              "korrigerer": null,
              "fnr": "12345678910",
              "fom": "2023-06-05",
              "tom": "2023-06-11"
            }
        """.trimIndent()
        val message = JsonMessage(json, MessageProblems(json)).apply {
            this.interestedIn("sendtArbeidsgiver", "sendtNav", "korrigerer", "fnr", "fom", "tom")
        }
        val sendtSøknad = message.toSendtSøknadNav()
        assertNull(sendtSøknad.korrigerer)
        assertEquals(LocalDateTime.parse("2023-06-01T00:00:00.0"), sendtSøknad.sendt)
    }
}
