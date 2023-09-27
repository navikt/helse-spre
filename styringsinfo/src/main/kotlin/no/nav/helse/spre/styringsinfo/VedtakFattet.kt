package no.nav.helse.spre.styringsinfo

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakFattet(
    val fom: LocalDate,
    val tom: LocalDate,
    val vedtakFattetTidspunkt: LocalDateTime,
    val hendelseId: UUID,
    val melding: String,
    val hendelser: List<UUID>,
    val patchLevel: Int? = null
)
