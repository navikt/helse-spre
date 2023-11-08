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
    val patchLevel: Int = 0
) {
    fun patch() = this
        .applyPatch(0, ::fjernFødselsnummerFraJsonString)
        .applyPatch(1, ::fjernOrganisasjonsnummerFraJsonString)

    private fun fjernFødselsnummerFraJsonString(vedtakFattet: VedtakFattet) =
        vedtakFattet.copy(melding = fjernRotNoderFraJson(vedtakFattet.melding, listOf("fødselsnummer")))

    private fun fjernOrganisasjonsnummerFraJsonString(vedtakFattet: VedtakFattet) =
        vedtakFattet.copy(melding = fjernRotNoderFraJson(vedtakFattet.melding, listOf("organisasjonsnummer")))

    private fun applyPatch(
        patchLevelPreCondition: Int,
        patchFunction: (input: VedtakFattet) -> VedtakFattet
    ) =
        if (this.patchLevel != patchLevelPreCondition) {
            this
        } else {
            patchFunction(this).copy(patchLevel = patchLevelPreCondition.inc())
        }
}

