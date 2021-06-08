package no.nav.helse.spre.saksbehandlingsstatistikk

import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class SerdeTest {

    @Test
    fun `serialisering av statistikkevent`() {
        global.setVersjon("kremfjes")

        val original = StatistikkEvent(
            aktorId = "banan",
            behandlingId = UUID.randomUUID(),
            tekniskTid = LocalDateTime.parse("2021-03-09T18:23:27.769"),
            funksjonellTid = LocalDateTime.parse("2023-03-09T18:23:27.769"),
            mottattDato = LocalDateTime.parse("2024-03-09T18:23:27.769").toString(),
            registrertDato = LocalDateTime.parse("2025-03-09T18:23:27.769").toString(),
            saksbehandlerIdent = "A121212",
            automatiskbehandling = null
        )

        val expectedString = """
            {
              "aktorId": "${original.aktorId}",
              "behandlingId": "${original.behandlingId}",
              "funksjonellTid": "${original.funksjonellTid}",
              "mottattDato": "${original.mottattDato}",
              "registrertDato": "${original.registrertDato}",
              "saksbehandlerIdent": "${original.saksbehandlerIdent}",
              "tekniskTid": "${original.tekniskTid}",
              "versjon": "kremfjes",
              "avsender": "SPLEIS",
              "ansvarligEnhetType": "NORG",
              "ansvarligEnhetKode": "4488",
              "totrinnsbehandling": "NEI",
              "utenlandstilsnitt": "NEI",
              "ytelseType": "SYKEPENGER",
              "behandlingStatus": "AVSLUTTET",
              "behandlingType": "SØKNAD"
            }
        """.trimIndent()

        TestUtil.assertJsonEquals(expectedString, objectMapper.writeValueAsString(original))
    }
}
