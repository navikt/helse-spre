package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.dataminimering.JsonUtil.fjernRotNoderFraJson
import no.nav.helse.spre.styringsinfo.dataminimering.JsonUtil.traverserOgFjernNoder
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
    val patchLevel: Int = 0
) {
    fun patch() = this
        .applyPatch(0, ::fjernFnrFraJsonString)
        .applyPatch(1, ::fjernArbeidsgiverFraJsonString)
        .applyPatch(2, ::fjernSpørsmålstekstFraJsonString)

    private fun fjernFnrFraJsonString(soknad: SendtSøknad) =
        soknad.copy(melding = fjernRotNoderFraJson(soknad.melding, listOf("fnr")))

    private fun fjernArbeidsgiverFraJsonString(soknad: SendtSøknad) =
        soknad.copy(melding = fjernRotNoderFraJson(soknad.melding, listOf("arbeidsgiver")))

    private fun fjernSpørsmålstekstFraJsonString(soknad: SendtSøknad) =
        soknad.copy(melding = traverserOgFjernNoder(soknad.melding, "sporsmalstekst"))

    private fun applyPatch(
        patchLevelPreCondition: Int,
        patchFunction: (input: SendtSøknad) -> SendtSøknad
    ) =
        if (this.patchLevel != patchLevelPreCondition) {
            this
        } else {
            patchFunction(this).copy(patchLevel = patchLevelPreCondition.inc())
        }
}
