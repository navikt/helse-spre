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
    val patchLevel: Int = VedtakForkastetPatch.UPATCHET.ordinal
) {
    fun patch() = this
        .applyPatch(VedtakForkastetPatch.FØDSELSNUMMER, ::fjernFødselsnummerFraJsonString)
        .applyPatch(VedtakForkastetPatch.ORGANISASJONSNUMMER, ::fjernOrganisasjonsnummerFraJsonString)

    private fun fjernFødselsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
        vedtakForkastet.copy(melding = fjernRotNoderFraJson(vedtakForkastet.melding, listOf("fødselsnummer")))

    private fun fjernOrganisasjonsnummerFraJsonString(vedtakForkastet: VedtakForkastet) =
        vedtakForkastet.copy(melding = fjernRotNoderFraJson(vedtakForkastet.melding, listOf("organisasjonsnummer")))

    private fun applyPatch(
        vedtakForkastetPatch: VedtakForkastetPatch,
        patchFunction: (input: VedtakForkastet) -> VedtakForkastet
    ) =
        if (this.patchLevel < vedtakForkastetPatch.ordinal) {
            patchFunction(this).copy(patchLevel = vedtakForkastetPatch.ordinal)
        } else {
            this
        }
}

enum class VedtakForkastetPatch {
    UPATCHET, FØDSELSNUMMER, ORGANISASJONSNUMMER
}
