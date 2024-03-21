package no.nav.helse.spre.styringsinfo.teamsak

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private val Oslo = ZoneId.of("Europe/Oslo")

internal val String.offsetDateTimeOslo get() = offsetDateTime(Oslo)
internal fun String.offsetDateTime(fallbackZoneId: ZoneId) = try {
    OffsetDateTime.parse(this)
} catch (_: DateTimeParseException) {
    LocalDateTime.parse(this).let {
        OffsetDateTime.of(it, fallbackZoneId.rules.getOffset(it))
    }
}

// TODO: Slutte å bruke når vi endrer fra timestamp til timestamptz
internal val OffsetDateTime.localDateTimeOslo get() = atZoneSameInstant(Oslo).toLocalDateTime()