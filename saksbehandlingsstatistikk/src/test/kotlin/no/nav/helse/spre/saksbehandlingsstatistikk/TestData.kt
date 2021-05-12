package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

object TestData {
    fun vedtaksperiodeEndretData() = VedtaksperiodeEndretData(listOf(), UUID.randomUUID())

    fun nyttDokumentData() = NyttDokumentData(
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MILLIS),
    )

    fun vedtaksperiodeGodkjent() = VedtaksperiodeGodkjentData(
        UUID.randomUUID(),
        randomIndent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
    )

    private fun randomIndent() = "${randomString(('A'..'Z'), 1)}${randomString(('0'..'9'), 6)}"

    fun vedtakFattet() = VedtakFattetData(
        LocalDateTime.now().minusHours(1).truncatedTo(ChronoUnit.MILLIS),
        randomIndent(),
        emptyList(),
        UUID.randomUUID(),
    )

    fun randomString(charPool: CharRange, length: Int) = (1..length)
        .map { i -> kotlin.random.Random.nextInt(0, charPool.toList().size) }
        .map(charPool.toList()::get)
        .joinToString("")

}