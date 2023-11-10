package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.dataminimering.JsonUtil.fjernRotNoderFraJson
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
    val patchLevel: Int = VedtakFattetPatch.UPATCHET.ordinal
) {
    fun patch() = this
        .applyPatch(VedtakFattetPatch.FØDSELSNUMMER, ::fjernFødselsnummerFraJsonString)
        .applyPatch(VedtakFattetPatch.ORGANISASJONSNUMMER, ::fjernOrganisasjonsnummerFraJsonString)

    private fun fjernFødselsnummerFraJsonString(vedtakFattet: VedtakFattet) =
        vedtakFattet.copy(melding = fjernRotNoderFraJson(vedtakFattet.melding, listOf("fødselsnummer")))

    private fun fjernOrganisasjonsnummerFraJsonString(vedtakFattet: VedtakFattet) =
        vedtakFattet.copy(melding = fjernRotNoderFraJson(vedtakFattet.melding, listOf("organisasjonsnummer")))

    private fun applyPatch(
        vedtakFattetPatch: VedtakFattetPatch,
        patchFunction: (input: VedtakFattet) -> VedtakFattet
    ) =
        if (this.patchLevel < vedtakFattetPatch.ordinal) {
            patchFunction(this).copy(patchLevel = vedtakFattetPatch.ordinal)
        } else {
            this
        }
}

enum class VedtakFattetPatch {
    UPATCHET, FØDSELSNUMMER, ORGANISASJONSNUMMER
}

