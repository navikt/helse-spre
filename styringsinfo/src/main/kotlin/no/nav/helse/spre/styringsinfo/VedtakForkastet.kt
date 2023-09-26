package no.nav.helse.spre.styringsinfo

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakForkastet(
    val fom: LocalDate,
    val tom: LocalDate,
    val forkastetTidspunkt: LocalDateTime,
    val hendelseId: UUID,
    val melding: String,
    val hendelser: List<UUID>
)
