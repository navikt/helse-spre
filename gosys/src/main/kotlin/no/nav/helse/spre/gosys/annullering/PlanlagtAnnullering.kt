package no.nav.helse.spre.gosys.annullering

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*

data class PlanlagtAnnullering(
    val id: UUID,
    val fnr: String,
    val yrkesaktivitetstype: String,
    val organisasjonsnummer: String?,
    val fom: LocalDate,
    val tom: LocalDate,
    val saksbehandlerIdent: String,
    val årsaker: List<String>,
    val begrunnelse: String,
    val vedtaksperioder: List<Vedtaksperiode>,
    val notat_opprettet: LocalDateTime?,
    val opprettet: LocalDateTime
) {
    fun erFerdigAnnullert(): Boolean {
        return vedtaksperioder.none { it.annullert == null }
    }

    fun erNotatOpprettet(): Boolean {
        return notat_opprettet != null
    }

    private val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
    val norskFom: String = fom.format(formatter)
    val norskTom: String = tom.format(formatter)

    internal fun toPdfPayload(organisasjonsnavn: String?, navn: String?) = FerdigAnnulleringPdfPayload(
        fødselsnummer = fnr,
        yrkesaktivitetstype = yrkesaktivitetstype,
        organisasjonsnummer = organisasjonsnummer,
        fom = fom,
        tom = tom,
        saksbehandlerIdent = saksbehandlerIdent,
        årsaker = årsaker,
        begrunnelse = begrunnelse,
        annullert = opprettet,
        organisasjonsnavn = organisasjonsnavn,
        navn = navn
    )

    data class FerdigAnnulleringPdfPayload(
        val fødselsnummer: String,
        val yrkesaktivitetstype: String,
        val organisasjonsnummer: String?,
        val fom: LocalDate,
        val tom: LocalDate,
        val saksbehandlerIdent: String,
        val årsaker: List<String>,
        val begrunnelse: String,
        val annullert: LocalDateTime,
        val organisasjonsnavn: String?,
        val navn: String?
    )

    data class Vedtaksperiode(val vedtaksperiodeId: UUID, val annullert: LocalDateTime?)
}
