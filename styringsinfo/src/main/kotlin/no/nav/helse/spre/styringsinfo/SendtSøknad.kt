package no.nav.helse.spre.styringsinfo

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class SendtSøknad(
    val sendt: LocalDateTime,
    val korrigerer: UUID?,
    val fom: LocalDate,
    val tom: LocalDate,
    val hendelseId: UUID,
    val melding: String,
    val patchLevel: String? = null
)