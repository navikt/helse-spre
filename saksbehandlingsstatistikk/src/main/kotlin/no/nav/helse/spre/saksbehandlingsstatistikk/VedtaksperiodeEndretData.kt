package no.nav.helse.spre.saksbehandlingsstatistikk

import java.util.*

class VedtaksperiodeEndretData(
    val hendelser: List<UUID>,
    val vedtaksperiodeId: UUID,
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
