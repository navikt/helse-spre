package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*

class VedtakFattetData(
    val opprettet: LocalDateTime,
    val aktørId: String,
    val hendelser: List<UUID>,
    val utbetalingId: UUID,
    val vedtaksperiodeId: UUID,
)

/*
queueMessage("vedtak_fattet", JsonMessage.newMessage(
    mutableMapOf(
    "vedtaksperiodeId" to vedtaksperiodeId,
    "fom" to periode.start,
    "tom" to periode.endInclusive,
    "hendelser" to hendelseIder,
    "skjæringstidspunkt" to skjæringstidspunkt,
    "sykepengegrunnlag" to sykepengegrunnlag,
    "inntekt" to inntekt
    ).apply {
        if (utbetalingId != null) this["utbetalingId"] = utbetalingId
    }
))*/
