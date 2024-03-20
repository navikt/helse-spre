package no.nav.helse.spre.styringsinfo.teamsak

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

internal class TidspunktTest {

    @Test
    fun `OffsetDateTime frem og tilbake høyre og venstre`() {
        val tidspunktUtenOffset = "2024-03-20T10:59:50.141422"
        val tidspunktMedOsloOffset = "2024-03-20T10:59:50.141422+01:00"
        val tidspunktMedUtcOffset = "2024-03-20T10:59:50.141422+00:00"
        assertThrows<DateTimeParseException> { OffsetDateTime.parse(tidspunktUtenOffset) }

        assertEquals(OffsetDateTime.parse(tidspunktMedOsloOffset), tidspunktUtenOffset.offsetDateTimeOslo)
        assertEquals(OffsetDateTime.parse(tidspunktMedUtcOffset), tidspunktUtenOffset.offsetDateTime(ZoneId.of("UTC")))

        assertEquals(LocalDateTime.parse("2024-03-20T11:59:50.141422"), OffsetDateTime.parse(tidspunktMedUtcOffset).localDateTimeOslo)
    }

}