package no.nav.helse.spre.saksbehandlingsstatistikk

import java.time.LocalDateTime
import java.util.*

class VedtaksperiodeEndretData(
    val opprettet: LocalDateTime,
    val hendelser: List<UUID>,
    val aktørId: String,
)

/*
queueMessage(
    "vedtaksperiode_endret", JsonMessage.newMessage(
        mapOf(
        "vedtaksperiodeId" to event.vedtaksperiodeId,
        "organisasjonsnummer" to event.organisasjonsnummer,
        "gjeldendeTilstand" to event.gjeldendeTilstand,
        "forrigeTilstand" to event.forrigeTilstand,
        "aktivitetslogg" to event.aktivitetslogg.toMap(),
        "harVedtaksperiodeWarnings" to event.harVedtaksperiodeWarnings,
        "hendelser" to event.hendelser,
        "makstid" to event.makstid
        )
    )
)*/
