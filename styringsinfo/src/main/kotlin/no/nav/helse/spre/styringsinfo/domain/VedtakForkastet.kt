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
    fun patch() = this.patch(0, ::fjernFødselsnummerFraJsonString, 1)
}

private fun fjernFødselsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
    vedtakForkastet.copy(melding = fjernNoderFraJson(vedtakForkastet.melding, listOf("fødselsnummer")))

private fun VedtakForkastet.patch(
    patchLevelPreCondition: Int,
    patchFunction: (input: VedtakForkastet) -> VedtakForkastet,
    patchLevelPostPatch: Int
): VedtakForkastet =
    if (this.patchLevel == patchLevelPreCondition)
        patchFunction(this).copy(patchLevel = patchLevelPostPatch)
    else
        this