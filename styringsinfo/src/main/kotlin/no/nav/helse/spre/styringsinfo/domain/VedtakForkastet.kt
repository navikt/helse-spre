package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.dataminimering.JsonUtil.fjernRotNoderFraJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class VedtakForkastet(
    val fom: LocalDate,
    val tom: LocalDate,
    val forkastetTidspunkt: LocalDateTime,
    val hendelseId: UUID,
    val melding: String,
    val hendelser: List<UUID>,
    val patchLevel: Int = 0
) {
    fun patch() = this
        .applyPatch(0, ::fjernFødselsnummerFraJsonString)
        .applyPatch(1, ::fjernOrganisasjonsnummerFraJsonString)

    private fun fjernFødselsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
        vedtakForkastet.copy(melding = fjernRotNoderFraJson(vedtakForkastet.melding, listOf("fødselsnummer")))

    private fun fjernOrganisasjonsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
        vedtakForkastet.copy(melding = fjernRotNoderFraJson(vedtakForkastet.melding, listOf("organisasjonsnummer")))

    private fun applyPatch(
        patchLevelPreCondition: Int,
        patchFunction: (input: VedtakForkastet) -> VedtakForkastet
    ) =
        if (this.patchLevel != patchLevelPreCondition) {
            this
        } else {
            patchFunction(this).copy(patchLevel = patchLevelPreCondition.inc())
        }
}
