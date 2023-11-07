package no.nav.helse.spre.styringsinfo.domain

import no.nav.helse.spre.styringsinfo.fjernNoderFraJson
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
        .patch(0, ::fjernFnrFraJsonString, 1)
        .patch(1, ::fjernArbeidsgiverFraJsonString, 2)
}

private fun fjernFnrFraJsonString(soknad: SendtSøknad) =
    soknad.copy(melding = fjernNoderFraJson(soknad.melding, listOf("fnr")))

private fun fjernArbeidsgiverFraJsonString(soknad: SendtSøknad) =
    soknad.copy(melding = fjernNoderFraJson(soknad.melding, listOf("arbeidsgiver")))

private fun SendtSøknad.patch(
    patchLevelPreCondition: Int,
    patchFunction: (input: SendtSøknad) -> SendtSøknad,
    patchLevelPostPatch: Int
): SendtSøknad =
    if (this.patchLevel != patchLevelPreCondition) {
        this
    } else {
        patchFunction(this).copy(patchLevel = patchLevelPostPatch)
    }
