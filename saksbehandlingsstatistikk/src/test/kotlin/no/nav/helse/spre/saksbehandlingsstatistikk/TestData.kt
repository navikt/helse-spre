package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*

object TestData {

    fun vedtaksperiodeEndretData(hendelse: UUID) = VedtaksperiodeEndretData(
        listOf(hendelse),
        UUID.randomUUID()
    )

    fun søknadData(korrigerer: UUID? = null) = SøknadData(
        UUID.randomUUID(),
        UUID.randomUUID(),
        LocalDateTime.now().minusDays(2).truncatedTo(ChronoUnit.MILLIS),
        korrigerer
    )

    fun vedtaksperiodeGodkjent(vedtaksperiodeId: UUID) = VedtaksperiodeGodkjentData(
        vedtaksperiodeId,
        randomIndent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
        true
    )

    private fun randomIndent() = "${randomString(('A'..'Z'), 1)}${randomString(('0'..'9'), 6)}"

    fun vedtakFattet(hendelser: List<UUID>, vedtaksperiodeId: UUID) = VedtakFattetData(
        randomIndent(),
        hendelser,
        vedtaksperiodeId,
        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
        false
    )

    fun vedtaksperiodeForkastet(vedtaksperiodeId: UUID) = VedtaksperiodeForkastetData(
        vedtaksperiodeId,
        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
        randomIndent(),
    )

    fun vedtaksperiodeAvvist(vedtaksperiodeId: UUID) = VedtaksperiodeAvvistData(
        vedtaksperiodeId,
        randomIndent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
        true
    )

    fun ikkeGodkjentGodkjenningBehovsLøsning(vedtaksperiodeId: UUID) = GodkjenningsBehovLøsningData(
        vedtaksperiodeId,
        randomIndent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
        true,
    )

    fun randomString(charPool: CharRange, length: Int) = (1..length)
        .map { _ -> kotlin.random.Random.nextInt(0, charPool.toList().size) }
        .map(charPool.toList()::get)
        .joinToString("")

}
