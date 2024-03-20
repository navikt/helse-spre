package no.nav.helse.spre.styringsinfo.teamsak

import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

private val Oslo = ZoneId.of("Europe/Oslo")

internal val String.offsetDateTimeOslo get() = offsetDateTime(Oslo)
internal fun String.offsetDateTime(zoneId: ZoneId) = try {
    OffsetDateTime.parse(this)
} catch (_: DateTimeParseException) {
    LocalDateTime.parse(this).let {
        OffsetDateTime.of(it, zoneId.rules.getOffset(it))
    }
}

internal val OffsetDateTime.localDateTimeOslo get() = localDateTime(Oslo)
internal fun OffsetDateTime.localDateTime(zoneId: ZoneId) = atZoneSameInstant(zoneId).toLocalDateTime()