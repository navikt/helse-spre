package no.nav.helse.spre.gosys.annullering

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class PlanlagtAnnulleringMessage(
    val hendelseId: UUID,
    val fødselsnummer: String,
    val yrkesaktivitet: String,
    val fom: LocalDate,
    val tom: LocalDate,
    val saksbehandlerIdent: String,
    val årsaker: List<String>,
    val begrunnelse: String,
    val vedtaksperioder: List<UUID>,
    val opprettet: LocalDateTime
)
