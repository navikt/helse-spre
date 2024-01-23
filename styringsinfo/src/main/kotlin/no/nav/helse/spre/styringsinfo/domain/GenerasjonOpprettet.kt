package no.nav.helse.spre.styringsinfo.domain

import java.time.LocalDateTime
import java.util.*

data class GenerasjonOpprettet(
        val aktørId: String,
        val vedtaksperiodeId: UUID,
        val generasjonId: UUID,
        val type: String,
        val kilde: Kilde,
        val hendelseId: UUID,
        val melding: String
)

data class Kilde (
        val meldingsreferanseId: UUID,
        val innsendt: LocalDateTime,
        val registrert: LocalDateTime,
        val avsender: String,
)
