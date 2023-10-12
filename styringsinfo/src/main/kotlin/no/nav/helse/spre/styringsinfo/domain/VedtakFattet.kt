package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.fjernNoderFraJson
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
    fun patch() = this.patch(0, ::fjernFødselsnummerFraJsonString, 1)
}

private fun fjernFødselsnummerFraJsonString(vedtakFattet: VedtakFattet) =
    vedtakFattet.copy(melding = fjernNoderFraJson(vedtakFattet.melding, listOf("fødselsnummer")))

private fun VedtakFattet.patch(
    patchLevelPreCondition: Int,
    patchFunction: (input: VedtakFattet) -> VedtakFattet,
    patchLevelPostPatch: Int
): VedtakFattet =
    if (this.patchLevel == patchLevelPreCondition)
        patchFunction(this).copy(patchLevel = patchLevelPostPatch)
    else
        this

