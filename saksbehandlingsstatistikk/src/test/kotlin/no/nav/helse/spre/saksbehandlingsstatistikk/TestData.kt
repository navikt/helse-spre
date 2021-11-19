package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.*
import kotlin.random.Random

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
        randomIdent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
        true
    )

    fun vedtakFattet(hendelser: List<UUID>, vedtaksperiodeId: UUID) = VedtakFattetData(
        randomIdent(),
        hendelser,
        vedtaksperiodeId,
        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
        false
    )

    fun vedtaksperiodeForkastet(vedtaksperiodeId: UUID) = VedtaksperiodeForkastetData(
        vedtaksperiodeId,
        LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS),
        randomIdent(),
    )

    fun vedtaksperiodeAvvist(vedtaksperiodeId: UUID) = VedtaksperiodeAvvistData(
        vedtaksperiodeId,
        randomIdent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
        true
    )

    fun ikkeGodkjentGodkjenningBehovsLøsning(vedtaksperiodeId: UUID) = GodkjenningsBehovLøsningData(
        vedtaksperiodeId,
        randomIdent(),
        LocalDateTime.now().minusDays(1).truncatedTo(ChronoUnit.MILLIS),
        true,
    )

    private fun randomIdent() = "${randomString(('A'..'Z'), 1)}${randomString(('0'..'9'), 6)}"

    private fun randomString(charPool: CharRange, length: Int) = (1..length)
        .map { Random.nextInt(0, charPool.count()) }
        .map(charPool.toList()::get)
        .joinToString()

}
