package no.nav.helse.spre.styringsinfo.domain

import java.time.LocalDateTime
import java.util.*

data class GenerasjonOpprettet(
        val fødselsnummer: String,
        val aktørId: String,
        val organisasjonsnummer: String,
        val vedtaksperiodeId: UUID,
        val generasjonId: UUID,
        val type: String,
        val kilde: Kilde,
)

data class Kilde (
        val meldingsreferanseId: UUID,
        val innsendt: LocalDateTime,
        val registert: LocalDateTime,
        val avsender: String,
)
