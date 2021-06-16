package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*

object TestData {
    fun vedtaksperiodeEndretData() = VedtaksperiodeEndretData(listOf(), UUID.randomUUID())

    fun søknadData() = SøknadData(
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDateTime.now().minusDays(2),
    )

    fun vedtaksperiodeGodkjent() = VedtaksperiodeGodkjentData(
        UUID.randomUUID(),
        randomIndent(),
        LocalDateTime.now().minusDays(1),
        true
    )

    private fun randomIndent() = "${randomString(('A'..'Z'), 1)}${randomString(('0'..'9'), 6)}"

    fun vedtakFattet() = VedtakFattetData(
        randomIndent(),
        emptyList(),
        UUID.randomUUID(),
        LocalDateTime.now(),
        false
    )

    fun vedtaksperiodeForkastet() = VedtaksperiodeForkastetData(
        UUID.randomUUID(),
        LocalDateTime.now(),
        randomIndent(),
    )

    fun vedtaksperiodeAvvist() = VedtaksperiodeAvvistData(
        UUID.randomUUID(),
        randomIndent(),
        LocalDateTime.now().minusDays(1),
        true
    )

    fun ikkeGodkjentGodkjenningBehovsLøsning() = GodkjenningsBehovLøsningData(
        UUID.randomUUID(),
        randomIndent(),
        LocalDateTime.now().minusDays(1),
        true,
    )

    fun randomString(charPool: CharRange, length: Int) = (1..length)
        .map { _ -> kotlin.random.Random.nextInt(0, charPool.toList().size) }
        .map(charPool.toList()::get)
        .joinToString("")

}
