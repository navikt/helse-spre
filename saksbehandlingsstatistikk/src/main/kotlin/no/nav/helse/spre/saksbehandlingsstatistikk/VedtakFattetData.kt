package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*

data class VedtakFattetData(
    val opprettet: LocalDateTime,
    val aktørId: String,
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
)
