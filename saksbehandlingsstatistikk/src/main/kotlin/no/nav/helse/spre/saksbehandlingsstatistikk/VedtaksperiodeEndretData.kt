package no.nav.helse.spre.saksbehandlingsstatistikk

import java.util.*

data class VedtaksperiodeEndretData(
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
)

