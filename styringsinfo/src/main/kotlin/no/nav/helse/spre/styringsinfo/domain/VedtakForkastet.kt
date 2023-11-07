package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.fjernNoderFraJson
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

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
        .patch(0, ::fjernFødselsnummerFraJsonString, 1)
        .patch(1, ::fjernOrganisasjonsnummerFraJsonString, 2)
}

private fun fjernFødselsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
    vedtakForkastet.copy(melding = fjernNoderFraJson(vedtakForkastet.melding, listOf("fødselsnummer")))

private fun fjernOrganisasjonsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
    vedtakForkastet.copy(melding = fjernNoderFraJson(vedtakForkastet.melding, listOf("organisasjonsnummer")))

private fun VedtakForkastet.patch(
    patchLevelPreCondition: Int,
    patchFunction: (input: VedtakForkastet) -> VedtakForkastet,
    patchLevelPostPatch: Int
): VedtakForkastet =
    if (this.patchLevel != patchLevelPreCondition) {
        this
    } else {
        patchFunction(this).copy(patchLevel = patchLevelPostPatch)
    }